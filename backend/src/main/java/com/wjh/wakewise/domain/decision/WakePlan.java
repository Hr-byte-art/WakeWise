package com.wjh.wakewise.domain.decision;

import java.time.Instant;
import java.util.List;

/**
 * 一次晨间唤醒计划。planId/status 为空表示仅预览，未落库。
 */
public record WakePlan(
        String planId,
        String alarmId,
        String status,
        Instant baselineWakeAt,
        Instant recommendedWakeAt,
        int deltaMinutes,
        Instant targetArrivalAt,
        double lateProbabilityBefore,
        double lateProbabilityAfter,
        double riskScore,
        double confidence,
        List<Factor> topFactors,
        String reasonCode,
        String explanation,
        boolean requiresConfirmation,
        String modelVersion) {

    public record Factor(String code, int impactMinutes, String label) {
    }
}
