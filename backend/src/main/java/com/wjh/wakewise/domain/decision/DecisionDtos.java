package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.domain.decision.DecisionHistory.PlanStatus;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class DecisionDtos {

    private DecisionDtos() {
    }

    public record EvaluateRequest(@NotBlank String alarmId) {
    }

    public record ConfirmRequest(String choice) {
    }

    public record RevertRequest(String reason) {
    }

    public record AckRequest(String deviceId, Integer planVersion, String localAlarmId,
                             String scheduledWakeAt, String platform) {
    }

    public record DeviceEventRequest(@NotBlank String eventId, @NotBlank String planId,
                                     @NotBlank String type, String occurredAt) {
    }

    public record PendingPlanView(String planId, String alarmId, String wakeAt, String targetAt,
                                  int deltaMinutes, String explanation, long version) {
    }

    public record PlanView(
            String planId,
            String alarmId,
            String status,
            String baselineWakeAt,
            String recommendedWakeAt,
            int deltaMinutes,
            String targetArrivalAt,
            RiskView risk,
            List<FactorView> topFactors,
            String explanation,
            String reasonCode,
            boolean requiresConfirmation,
            String modelVersion,
            long version,
            String effectiveAt) {

        public record RiskView(double score, double lateProbabilityBefore,
                               double lateProbabilityAfter, double confidence) {
        }

        public record FactorView(String code, int impactMinutes, String label) {
        }

        public static PlanView from(WakePlan plan) {
            String status = plan.status() == null ? "PREVIEW" : plan.status();
            return new PlanView(plan.planId(), plan.alarmId(), status,
                    plan.baselineWakeAt().toString(),
                    plan.recommendedWakeAt().toString(),
                    plan.deltaMinutes(),
                    plan.targetArrivalAt().toString(),
                    new RiskView(plan.riskScore(), plan.lateProbabilityBefore(),
                            plan.lateProbabilityAfter(), plan.confidence()),
                    plan.topFactors() == null ? List.of() : plan.topFactors().stream()
                            .map(f -> new FactorView(f.code(), f.impactMinutes(), f.label()))
                            .toList(),
                    plan.explanation(),
                    plan.reasonCode(),
                    plan.requiresConfirmation(),
                    plan.modelVersion(),
                    0,
                    null);
        }

        public static PlanView from(DecisionHistory history) {
            List<FactorView> factors = parseFactors(history.getFactorContributionJson());
            return new PlanView(history.getPlanId(), history.getAlarmId(), history.getStatus().name(),
                    history.getBaselineAt().toString(),
                    history.getRecommendedAt().toString(),
                    history.getDeltaMinutes(),
                    history.getTargetAt().toString(),
                    new RiskView(history.getRiskScore().doubleValue(),
                            history.getLateProbabilityBefore().doubleValue(),
                            history.getLateProbabilityAfter().doubleValue(),
                            history.getConfidence().doubleValue()),
                    factors,
                    history.getExplanation(),
                    history.getReasonCode(),
                    history.getStatus() == PlanStatus.PROPOSED,
                    history.getModelVersion(),
                    history.getVersion(),
                    history.getEffectiveAt() == null ? null : history.getEffectiveAt().toString());
        }

        private static List<FactorView> parseFactors(String json) {
            try {
                List<WakePlan.Factor> factors = ObjectMapperHolder.MAPPER.readValue(
                        json == null ? "[]" : json,
                        ObjectMapperHolder.MAPPER.getTypeFactory()
                                .constructCollectionType(List.class, WakePlan.Factor.class));
                return factors.stream()
                        .map(f -> new FactorView(f.code(), f.impactMinutes(), f.label()))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    /** 延迟初始化的共享 ObjectMapper，避免与 Spring 生命周期耦合。 */
    private static final class ObjectMapperHolder {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }

    static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
