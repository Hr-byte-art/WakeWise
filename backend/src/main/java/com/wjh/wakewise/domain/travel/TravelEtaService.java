package com.wjh.wakewise.domain.travel;

import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.user.CredentialService;
import com.wjh.wakewise.domain.user.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 真实通勤估算编排（镜像 WeatherService 模式）：坐标解析 → 路径规划 → 2 小时快照缓存。
 *
 * 高德 key 由调用方经 CredentialService 解析后传入（用户级 BYOK ＞ 服务端默认）；
 * access 不可用（OFF/已禁用）时整体不查询，返回 unavailable。
 * 坐标来源：user/alarm 上的地理编码缓存列（建闹钟/设常驻地址时写入）；
 * 列为空时现查地理编码但不回写——估算可能在只读事务/持有行锁的事务里被调用，
 * 回写会踩 readOnly 不落库或自锁的坑，地址变更点的写入职责在 AlarmService/UserService。
 * 任何失败返回 TravelTime.unavailable()，引擎回落用户手填通勤分钟。
 */
@Service
public class TravelEtaService {

    private static final Logger log = LoggerFactory.getLogger(TravelEtaService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(2);
    /** 外部真实数据的质量分（略低于天气 0.95：路径规划不含实时路况，仅规划耗时）。 */
    private static final BigDecimal QUALITY_SCORE = new BigDecimal("0.90");

    private final AmapClient amapClient;
    private final TrafficSnapshotRepository repository;

    public TravelEtaService(AmapClient amapClient, TrafficSnapshotRepository repository) {
        this.amapClient = amapClient;
        this.repository = repository;
    }

    public TravelTime estimate(UserAccount user, Alarm alarm, CredentialService.AmapAccess access) {
        try {
            if (access == null || !access.available()) {
                return TravelTime.unavailable();
            }
            String key = access.key();
            Alarm.TravelMode mode = alarm == null ? null : alarm.getTravelMode();
            if (mode == null || mode == Alarm.TravelMode.NONE) {
                return TravelTime.unavailable();
            }
            String origin = resolveOrigin(user, key);
            String destination = resolveDestination(alarm, key);
            if (origin == null || destination == null) {
                return TravelTime.unavailable();
            }

            String routeKey = routeKey(origin, destination, mode.name());
            Instant now = Instant.now();
            Optional<TrafficSnapshot> cached =
                    repository.findFirstByRouteKeyAndExpiresAtAfterOrderByObservedAtDesc(routeKey, now);
            if (cached.isPresent()) {
                int minutes = (cached.get().getEtaP50Sec() + 59) / 60;
                return new TravelTime(true, minutes, cached.get().getQualityScore().doubleValue());
            }

            Optional<Integer> seconds = amapClient.routeDurationSeconds(origin, destination, mode, user.getCity(), key);
            if (seconds.isEmpty()) {
                return TravelTime.unavailable();
            }
            int p50Sec = seconds.get();
            saveSnapshot(routeKey, mode, p50Sec, now);
            return new TravelTime(true, (p50Sec + 59) / 60, QUALITY_SCORE.doubleValue());
        } catch (Exception e) {
            // 增强链路：失败只记日志，绝不影响决策主流程
            log.warn("Travel estimate failed: alarm={}, {}", alarm == null ? "?" : alarm.getId(), e.getMessage());
            return TravelTime.unavailable();
        }
    }

    /** 缓存键："o<lng,lat 4dp>|d<...>|MODE"，恒 ≤ 64 字符（列宽）。纯函数可测。 */
    static String routeKey(String origin, String destination, String mode) {
        return "o" + compact(origin) + "|d" + compact(destination) + "|m" + mode;
    }

    private static String compact(String lngLat) {
        String[] parts = lngLat.split(",");
        if (parts.length != 2) {
            return lngLat;
        }
        return new BigDecimal(parts[0].trim()).setScale(4, RoundingMode.HALF_UP) + ","
                + new BigDecimal(parts[1].trim()).setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveOrigin(UserAccount user, String key) {
        if (user.getHomeLng() != null && user.getHomeLat() != null) {
            return user.getHomeLng().toPlainString() + "," + user.getHomeLat().toPlainString();
        }
        return amapClient.geocode(user.getHomeAddress(), key).orElse(null);
    }

    private String resolveDestination(Alarm alarm, String key) {
        if (alarm.getDestLng() != null && alarm.getDestLat() != null) {
            return alarm.getDestLng().toPlainString() + "," + alarm.getDestLat().toPlainString();
        }
        return amapClient.geocode(alarm.getDestinationLabel(), key).orElse(null);
    }

    /** 快照写入是 best-effort：在只读调用方事务里可能不落库，仅损失缓存、不影响本次结果。 */
    private void saveSnapshot(String routeKey, Alarm.TravelMode mode, int p50Sec, Instant now) {
        try {
            TrafficSnapshot snapshot = new TrafficSnapshot();
            snapshot.setId(Ids.newId());
            snapshot.setProvider("AMAP");
            snapshot.setRouteKey(routeKey);
            snapshot.setTravelMode(mode.name());
            snapshot.setEtaP50Sec(p50Sec);
            snapshot.setEtaP90Sec((int) Math.round(p50Sec * p90Factor(mode)));
            snapshot.setQualityScore(QUALITY_SCORE);
            snapshot.setObservedAt(now);
            snapshot.setExpiresAt(now.plus(CACHE_TTL));
            repository.save(snapshot);
        } catch (Exception e) {
            log.warn("Traffic snapshot save failed (cache only): {}", e.getMessage());
        }
    }

    private static double p90Factor(Alarm.TravelMode mode) {
        Double factor = com.wjh.wakewise.domain.decision.DecisionEngine.TRAVEL_P90_FACTOR.get(mode);
        return factor == null ? 1.0 : factor;
    }
}
