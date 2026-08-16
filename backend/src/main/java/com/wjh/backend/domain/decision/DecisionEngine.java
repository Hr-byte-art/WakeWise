package com.wjh.backend.domain.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.wjh.backend.domain.alarm.Alarm;
import com.wjh.backend.domain.alarm.AlarmRule;
import com.wjh.backend.domain.weather.WeatherCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 规则决策引擎
 * 基于天气因子加权计算提前量，生成 WakePlan
 */
@Component
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private final WeatherFactorCalculator weatherFactorCalculator;
    private final ObjectMapper objectMapper;

    public DecisionEngine(WeatherFactorCalculator weatherFactorCalculator, ObjectMapper objectMapper) {
        this.weatherFactorCalculator = weatherFactorCalculator;
        this.objectMapper = objectMapper;
    }

    /**
     * 评估并生成唤醒计划
     *
     * @param alarm      闹钟
     * @param rule       闹钟规则
     * @param weather    天气数据（可为 null）
     * @param targetDate 目标日期
     * @return WakePlan
     */
    public WakePlan evaluate(Alarm alarm, AlarmRule rule, WeatherCache weather, Instant targetDate) {
        // 1. 计算基准唤醒时间
        Instant baselineWakeAt = computeBaseline(alarm, rule, targetDate);

        // 2. 计算天气影响系数
        WeatherFactorResult weatherFactor;
        double confidence;
        if (weather != null && weather.getQualityScore().compareTo(BigDecimal.ZERO) > 0) {
            weatherFactor = weatherFactorCalculator.calculate(weather);
            confidence = weather.getQualityScore().doubleValue();
        } else {
            weatherFactor = weatherFactorCalculator.neutral();
            confidence = 0.5; // 无天气数据，低置信度
        }

        // 3. 计算调整量
        double coefficient = weatherFactor.compositeCoefficient().doubleValue();
        int prepMinutes = rule.getPrepDurationMin();
        int weatherImpactMinutes = (int) Math.round(prepMinutes * (coefficient - 1.0));

        // 4. 应用硬约束
        int maxAdvance = rule.getMaxAdvanceMin();
        int maxDelay = rule.getMaxDelayMin();
        int deltaMinutes = -Math.min(weatherImpactMinutes, maxAdvance); // 负值 = 提前

        // 5. 计算推荐唤醒时间
        Instant recommendedWakeAt = baselineWakeAt.plus(deltaMinutes, ChronoUnit.MINUTES);

        // 6. 计算迟到概率（简化：系数越高概率越高）
        double lateProb = Math.min(0.5, (coefficient - 1.0) * 0.3);

        // 7. 计算风险分数
        double riskScore = coefficient * 10.0;

        // 8. 生成因素贡献列表（按影响分钟排序）
        List<WakePlan.FactorContribution> topFactors = buildTopFactors(weatherFactor, prepMinutes);

        // 9. 判断是否需要确认
        boolean requiresConfirmation = confidence < 0.6
                || Math.abs(deltaMinutes) > 15
                || rule.getAutomationLevel() < 2;

        // 10. 生成原因码
        String reasonCode = deltaMinutes < -5 ? "WEATHER_ADVERSE" : "WEATHER_NORMAL";

        String planId = UlidCreator.getUlid().toString();

        return new WakePlan(
                planId,
                alarm.getId(),
                alarm.getUserId(),
                baselineWakeAt,
                recommendedWakeAt,
                deltaMinutes,
                alarm.getTargetAt(),
                riskScore,
                lateProb,
                confidence,
                topFactors,
                reasonCode,
                "", // explanation 由 LangChain4j 生成
                weather != null ? weather.getObservedAt() : null,
                requiresConfirmation,
                Instant.now().plus(1, ChronoUnit.HOURS),
                1
        );
    }

    /**
     * 计算基准唤醒时间
     */
    private Instant computeBaseline(Alarm alarm, AlarmRule rule, Instant targetDate) {
        return switch (alarm.getTargetType()) {
            case WAKE_AT -> {
                if (alarm.getBaselineTime() != null) {
                    yield targetDate.atZone(java.time.ZoneOffset.UTC)
                            .with(alarm.getBaselineTime())
                            .toInstant();
                }
                yield targetDate;
            }
            case LEAVE_BY, ARRIVE_BY, EVENT -> {
                if (alarm.getTargetAt() != null) {
                    yield alarm.getTargetAt().minus(rule.getPrepDurationMin(), ChronoUnit.MINUTES);
                }
                yield targetDate;
            }
        };
    }

    private List<WakePlan.FactorContribution> buildTopFactors(WeatherFactorResult result, int prepMinutes) {
        return result.contributions().stream()
                .filter(c -> c.intensity() > 1.05)
                .sorted(Comparator.comparingDouble(WeatherFactorResult.FactorContribution::impact).reversed())
                .limit(3)
                .map(c -> new WakePlan.FactorContribution(
                        c.factorCode(),
                        (int) Math.round(c.impact() * prepMinutes)
                ))
                .toList();
    }

    /**
     * 将 WakePlan 持久化为 DecisionHistory
     */
    public DecisionHistory toEntity(WakePlan plan) {
        DecisionHistory dh = new DecisionHistory();
        dh.setPlanId(plan.planId());
        dh.setUserId(plan.userId());
        dh.setAlarmId(plan.alarmId());
        dh.setBaselineAt(plan.baselineWakeAt());
        dh.setRecommendedAt(plan.recommendedWakeAt());
        dh.setStatus(plan.requiresConfirmation()
                ? DecisionHistory.DecisionStatus.PENDING
                : DecisionHistory.DecisionStatus.PROPOSED);
        dh.setRiskScore(BigDecimal.valueOf(plan.riskScore()));
        dh.setLateProbability(BigDecimal.valueOf(plan.lateProbability()));
        dh.setConfidence(BigDecimal.valueOf(plan.confidence()));
        dh.setReasonCode(plan.reasonCode());
        dh.setExplanation(plan.explanation());
        dh.setPolicyVersion("v1.0.0");
        dh.setModelVersion("v1.0.0");

        try {
            dh.setFactorContributionJson(objectMapper.writeValueAsString(plan.topFactors()));
            dh.setContextSnapshotJson("{}");
        } catch (JsonProcessingException e) {
            dh.setFactorContributionJson("[]");
            dh.setContextSnapshotJson("{}");
        }

        return dh;
    }
}