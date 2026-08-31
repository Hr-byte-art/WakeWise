package com.wjh.wakewise.domain.weather;

import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.user.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 晨间窗口（05:00-10:00 本地时间）天气风险。
 * 读取顺序：新鲜缓存 -> Open-Meteo 拉取；外部失败时返回 unavailable，由决策引擎降级。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final int WINDOW_START_HOUR = 5;
    private static final int WINDOW_END_HOUR = 10;
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    private final WeatherSnapshotRepository snapshotRepository;
    private final OpenMeteoClient openMeteoClient;

    public WeatherService(WeatherSnapshotRepository snapshotRepository, OpenMeteoClient openMeteoClient) {
        this.snapshotRepository = snapshotRepository;
        this.openMeteoClient = openMeteoClient;
    }

    @Transactional
    public WeatherRisk getMorningRisk(UserAccount user, LocalDate targetDate, ZoneId zone) {
        String city = user.getCity();
        if (city == null || city.isBlank()) {
            return WeatherRisk.unavailable();
        }
        Instant windowStart = targetDate.atTime(WINDOW_START_HOUR, 0).atZone(zone).toInstant();
        try {
            Optional<WeatherSnapshot> cached = snapshotRepository
                    .findFirstByCityAndForecastTimeAndExpiresAtAfterOrderByObservedAtDesc(city, windowStart, Instant.now());
            WeatherSnapshot snapshot = cached.orElseGet(() -> fetchAndCache(user, targetDate, city, zone, windowStart));
            return toRisk(snapshot);
        } catch (Exception e) {
            log.warn("Weather risk degraded to unavailable: {}", e.getMessage());
            return WeatherRisk.unavailable();
        }
    }

    private WeatherSnapshot fetchAndCache(UserAccount user, LocalDate targetDate, String city, ZoneId zone,
                                          Instant windowStart) {
        try {
            Optional<OpenMeteoClient.GeoLocation> geo = openMeteoClient.geocode(city);
            if (geo.isEmpty()) {
                return null;
            }
            Optional<OpenMeteoClient.HourlyForecast> forecast =
                    openMeteoClient.forecast(geo.get().latitude(), geo.get().longitude(), targetDate);
            if (forecast.isEmpty()) {
                return null;
            }

            double tempSum = 0;
            int tempCount = 0;
            double precipProbMax = 0;
            double precipMmTotal = 0;
            int codeMax = 0;
            OpenMeteoClient.HourlyForecast hourly = forecast.get();
            for (int i = 0; i < hourly.time().size(); i++) {
                LocalTime hour = LocalTime.parse(hourly.time().get(i).substring(11));
                if (hour.getHour() < WINDOW_START_HOUR || hour.getHour() >= WINDOW_END_HOUR) {
                    continue;
                }
                tempSum += hourly.temperatureC().get(i);
                tempCount++;
                precipProbMax = Math.max(precipProbMax, hourly.precipProbability().get(i));
                precipMmTotal += hourly.precipMm().get(i);
                codeMax = Math.max(codeMax, hourly.weatherCode().get(i));
            }
            if (tempCount == 0) {
                return null;
            }

            WeatherSnapshot snapshot = new WeatherSnapshot();
            snapshot.setId(Ids.newId());
            snapshot.setProvider("open-meteo");
            snapshot.setCity(city);
            snapshot.setForecastTime(windowStart);
            snapshot.setObservedAt(Instant.now());
            snapshot.setTemperatureC(round(tempSum / tempCount, 2));
            snapshot.setPrecipProbability(round(precipProbMax / 100.0, 4));
            snapshot.setPrecipMm(round(precipMmTotal, 2));
            snapshot.setWeatherCode(codeMax);
            snapshot.setAlertLevel(codeMax >= 95 ? "WARNING" : null);
            snapshot.setQualityScore(BigDecimal.valueOf(0.95));
            snapshot.setExpiresAt(Instant.now().plus(CACHE_TTL));
            return snapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("Weather fetch failed for city {}: {}", city, e.getMessage());
            return null;
        }
    }

    private WeatherRisk toRisk(WeatherSnapshot snapshot) {
        if (snapshot == null || snapshot.getPrecipProbability() == null) {
            return WeatherRisk.unavailable();
        }
        double precipProbability = snapshot.getPrecipProbability().doubleValue();
        double precipMm = snapshot.getPrecipMm() == null ? 0 : snapshot.getPrecipMm().doubleValue();
        String level = precipProbability >= 0.60 || precipMm >= 5.0 ? WeatherRisk.LEVEL_HEAVY
                : precipProbability >= 0.30 || precipMm >= 1.5 ? WeatherRisk.LEVEL_MODERATE
                : precipProbability >= 0.05 || precipMm >= 0.2 ? WeatherRisk.LEVEL_LIGHT
                : WeatherRisk.LEVEL_NONE;
        return new WeatherRisk(true, level, precipProbability, precipMm,
                snapshot.getTemperatureC() == null ? 0 : snapshot.getTemperatureC().doubleValue(),
                snapshot.getWeatherCode(), snapshot.getAlertLevel(),
                snapshot.getQualityScore().doubleValue());
    }

    private BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
