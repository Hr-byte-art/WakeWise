package com.wjh.backend.domain.decision;

import java.time.Instant;
import java.util.List;

/**
 * 唤醒计划 — 决策引擎的输出
 */
public record WakePlan(
        String planId,
        String alarmId,
        String userId,
        Instant baselineWakeAt,       // 原计划唤醒时间
        Instant recommendedWakeAt,    // 推荐唤醒时间
        int deltaMinutes,             // 调整分钟数（负=提前）
        Instant targetArrivalAt,      // 目标到达时间
        double riskScore,             // 风险分数
        double lateProbability,       // 迟到概率
        double confidence,            // 置信度
        List<FactorContribution> topFactors,  // 主要影响因素
        String reasonCode,            // 主原因码
        String explanation,           // 解释文案
        Instant weatherObservedAt,    // 天气数据时间
        boolean requiresConfirmation, // 是否需要确认
        Instant expiresAt,            // 计划过期时间
        long version
) {
    public record FactorContribution(
            String code,
            int impactMinutes
    ) {}
}