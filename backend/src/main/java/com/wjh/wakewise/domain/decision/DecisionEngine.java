package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRule;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 分位数风险约束决策引擎（PRD 第 13 章）：
 *
 *   RequiredTime      = WakeLatency + PreparationTime + TravelTime + SafetyBuffer
 *   CandidateWakeTime = D - Quantile(RequiredTime, q)
 *
 * q 由用户风险偏好决定（SLEEP_FIRST≈P60 / BALANCED≈P75 / CONSERVATIVE≈P90）。
 * 结果硬约束在 [baseline - maxAdvance, baseline + maxDelay] 内，且不晚于目标前 15 分钟。
 * 引擎是确定性的：同样输入必然产生同样输出，便于测试、审计与回滚。
 */
@Component
public class DecisionEngine {

    public static final String MODEL_VERSION = "QUANTILE_V1";

    public static final double WAKE_LATENCY_P50_MIN = 4.0;
    public static final double WAKE_LATENCY_P90_MIN = 8.0;

    private static final double PREP_P90_FACTOR = 1.25;
    private static final double Z_60 = 0.2533;
    private static final double Z_75 = 0.6745;
    private static final double Z_90 = 1.2816;
    private static final double NORMAL_P90_Z = 1.2816;

    private static final Map<Alarm.TravelMode, Double> TRAVEL_P90_FACTOR = Map.of(
            Alarm.TravelMode.WALK, 1.20,
            Alarm.TravelMode.BIKE, 1.30,
            Alarm.TravelMode.TRANSIT, 1.50,
            Alarm.TravelMode.DRIVE, 1.60,
            Alarm.TravelMode.MIXED, 1.70,
            Alarm.TravelMode.NONE, 1.00);

    private static final double RAIN_TRAVEL_TRAIL_HEAVY = 1.35;
    private static final double RAIN_TRAVEL_TRAIL_MODERATE = 1.20;
    private static final int GRID_MINUTES = 5;
    private static final int HARD_FLOOR_BEFORE_TARGET_MIN = 15;

    private final double bufferBalanced;
    private final double bufferConservative;
    private final double bufferSleepFirst;

    public DecisionEngine(@Value("${wakewise.decision.safety-buffer-minutes-balanced:5}") double bufferBalanced,
                          @Value("${wakewise.decision.safety-buffer-minutes-conservative:8}") double bufferConservative,
                          @Value("${wakewise.decision.safety-buffer-minutes-sleep-first:3}") double bufferSleepFirst) {
        this.bufferBalanced = bufferBalanced;
        this.bufferConservative = bufferConservative;
        this.bufferSleepFirst = bufferSleepFirst;
    }

    public WakePlan evaluate(Alarm alarm, AlarmRule rule, UserAccount user, WeatherRisk weather,
                             Instant targetArrivalAt, Integer overrideMaxAdvance) {
        boolean conservative = user.getRiskPreference() == UserAccount.RiskPreference.CONSERVATIVE;
        boolean sleepFirst = user.getRiskPreference() == UserAccount.RiskPreference.SLEEP_FIRST;
        double z = conservative ? Z_90 : sleepFirst ? Z_60 : Z_75;
        double safetyBuffer = conservative ? bufferConservative : sleepFirst ? bufferSleepFirst : bufferBalanced;

        // 1. 各阶段耗时的分位数（正态近似，sigma 由 p50/p90 推出）
        double wakeSigma = (WAKE_LATENCY_P90_MIN - WAKE_LATENCY_P50_MIN) / NORMAL_P90_Z;
        double wakeQuantile = WAKE_LATENCY_P50_MIN + z * wakeSigma;

        double prepP50 = Math.max(0, rule.getPrepDurationMin());
        double prepSigma = prepP50 * (PREP_P90_FACTOR - 1.0) / NORMAL_P90_Z;
        double prepQuantile = prepP50 + z * prepSigma;

        double travelP50 = Math.max(0, rule.getCommuteMinutes());
        Alarm.TravelMode mode = alarm.getTravelMode() == null ? Alarm.TravelMode.NONE : alarm.getTravelMode();
        double travelBaseP90 = travelP50 * TRAVEL_P90_FACTOR.getOrDefault(mode, 1.0);
        double weatherTrail = weather.heavy() ? RAIN_TRAVEL_TRAIL_HEAVY
                : weather.moderate() ? RAIN_TRAVEL_TRAIL_MODERATE
                : 1.0;
        double travelP90 = travelBaseP90 * weatherTrail;
        double travelSigma = travelP50 > 0 ? (travelP90 - travelP50) / NORMAL_P90_Z : 0;
        double travelQuantile = travelP50 + z * travelSigma;

        // 2. 基准唤醒（用户凭 p50 经验会设的时间）与理想唤醒（分位数 + 安全余量）
        double requiredMean = WAKE_LATENCY_P50_MIN + prepP50 + travelP50;
        double requiredSigma = Math.sqrt(wakeSigma * wakeSigma + prepSigma * prepSigma + travelSigma * travelSigma);
        double requiredQuantile = wakeQuantile + prepQuantile + travelQuantile;

        Instant baselineWakeAt = targetArrivalAt.minus((long) Math.ceil(requiredMean), ChronoUnit.MINUTES);
        Instant ideal = targetArrivalAt.minus((long) Math.ceil(requiredQuantile + safetyBuffer), ChronoUnit.MINUTES);

        // 3. 硬约束：调整边界 + 不晚于目标前 15 分钟
        int maxAdvance = overrideMaxAdvance != null ? overrideMaxAdvance : rule.getMaxAdvanceMin();
        Instant earliest = baselineWakeAt.minus(maxAdvance, ChronoUnit.MINUTES);
        Instant latest = rule.isAllowAutoDelay()
                ? baselineWakeAt.plus(rule.getMaxDelayMin(), ChronoUnit.MINUTES)
                : baselineWakeAt;
        Instant hardFloor = targetArrivalAt.minus(HARD_FLOOR_BEFORE_TARGET_MIN, ChronoUnit.MINUTES);
        if (latest.isAfter(hardFloor)) {
            latest = hardFloor;
        }

        Instant recommended = quantize(ideal);
        boolean capped = false;
        if (recommended.isBefore(earliest)) {
            recommended = earliest;
            capped = true;
        }
        if (recommended.isAfter(latest)) {
            recommended = latest;
        }

        // 3.5 计划变化成本：无外部风险信号（无雨）且提前量不足 10 分钟时不折腾，保持原计划
        if (weatherTrail == 1.0 && !capped && recommended.isBefore(baselineWakeAt)
                && ChronoUnit.MINUTES.between(recommended, baselineWakeAt) < 10) {
            recommended = baselineWakeAt;
        }

        // 4. 风险与置信度
        double lateProbabilityBefore = lateProbability(baselineWakeAt, targetArrivalAt, requiredMean, requiredSigma);
        double lateProbabilityAfter = lateProbability(recommended, targetArrivalAt, requiredMean, requiredSigma);
        double confidence = weather.available() ? 0.65 + 0.30 * weather.qualityScore() : 0.55;
        double eventCost = conservative ? 1.2 : sleepFirst ? 0.8 : 1.0;
        double riskScore = 100.0 * (lateProbabilityAfter * eventCost + (1 - confidence) * 0.15);

        // 5. 可解释因素（对 baseline 的增量归因）
        List<WakePlan.Factor> factors = new ArrayList<>();
        if (travelP50 > 0) {
            if (weatherTrail > 1.0) {
                factors.add(new WakePlan.Factor("TRAFFIC_P90_INCREASE",
                        (int) Math.round(travelP90 - travelBaseP90), "降水增加通勤波动"));
            }
            factors.add(new WakePlan.Factor("TRAVEL_TAIL_RISK",
                    (int) Math.round(travelBaseP90 - travelP50), "通勤时长波动余量"));
        }
        factors.add(new WakePlan.Factor("WAKE_LATENCY_MARGIN",
                (int) Math.round(wakeQuantile - WAKE_LATENCY_P50_MIN), "起床延迟余量"));
        factors.add(new WakePlan.Factor("SAFETY_BUFFER", (int) safetyBuffer, "安全余量"));
        factors.sort(Comparator.comparingInt(WakePlan.Factor::impactMinutes).reversed());
        factors = factors.stream().filter(f -> f.impactMinutes() > 0).limit(3).toList();

        long deltaMinutes = ChronoUnit.MINUTES.between(baselineWakeAt, recommended);
        String reasonCode;
        if (deltaMinutes == 0) {
            reasonCode = "KEEP_BASELINE";
        } else if (deltaMinutes < 0) {
            if (capped) {
                reasonCode = "ADVANCE_CAPPED";
            } else if (weatherTrail > 1.0) {
                reasonCode = "WEATHER_TRAIL_INCREASE";
            } else {
                reasonCode = "RISK_QUANTILE_MARGIN";
            }
        } else {
            reasonCode = "SLEEP_PRESERVED";
        }

        return new WakePlan(null, alarm.getId(), null,
                baselineWakeAt, recommended, (int) deltaMinutes, targetArrivalAt,
                lateProbabilityBefore, lateProbabilityAfter, riskScore, confidence,
                factors, reasonCode, null, false, MODEL_VERSION);
    }

    /** 用户凭 p50 经验会设置的基准唤醒时间（MorningContext 与测试使用）。 */
    public Instant baselineWakeAt(AlarmRule rule, Instant targetArrivalAt) {
        double requiredMean = WAKE_LATENCY_P50_MIN + Math.max(0, rule.getPrepDurationMin())
                + Math.max(0, rule.getCommuteMinutes());
        return targetArrivalAt.minus((long) Math.ceil(requiredMean), ChronoUnit.MINUTES);
    }

    /** 建议出门时间：推荐唤醒 + 起床延迟 P90 + 准备时长 P50。 */
    public Instant leaveBy(AlarmRule rule, Instant recommendedWakeAt) {
        double prepP50 = Math.max(0, rule.getPrepDurationMin());
        long minutes = (long) Math.ceil(WAKE_LATENCY_P90_MIN + prepP50);
        return recommendedWakeAt.plus(minutes, ChronoUnit.MINUTES);
    }

    private double lateProbability(Instant wakeAt, Instant targetArrivalAt, double mean, double sigma) {
        double travelToTarget = ChronoUnit.SECONDS.between(wakeAt, targetArrivalAt) / 60.0;
        if (sigma <= 0) {
            return travelToTarget < mean ? 1.0 : 0.0;
        }
        double x = (travelToTarget - mean) / sigma;
        return 1.0 - phi(x);
    }

    private static double phi(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2)));
    }

    /** Abramowitz & Stegun 7.1.26 误差函数近似，|epsilon| <= 1.5e-7。 */
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }

    private Instant quantize(Instant t) {
        long gridSeconds = GRID_MINUTES * 60L;
        return Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), gridSeconds) * gridSeconds);
    }
}
