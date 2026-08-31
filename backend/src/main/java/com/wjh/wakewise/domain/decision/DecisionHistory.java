package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "decision_history")
public class DecisionHistory extends BaseEntity {

    public enum PlanStatus { PROPOSED, DISPATCHING, ARMED, REVERTED, EXECUTED, SUPERSEDED, FAILED }

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "plan_id", nullable = false, unique = true, length = 26)
    private String planId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "alarm_id", nullable = false, length = 26)
    private String alarmId;

    @Column(name = "baseline_at", nullable = false)
    private Instant baselineAt;

    @Column(name = "recommended_at", nullable = false)
    private Instant recommendedAt;

    @Column(name = "target_at", nullable = false)
    private Instant targetAt;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PlanStatus status;

    @Column(name = "delta_minutes", nullable = false)
    private int deltaMinutes;

    @Column(name = "late_probability_before", nullable = false, precision = 6, scale = 5)
    private BigDecimal lateProbabilityBefore;

    @Column(name = "late_probability_after", nullable = false, precision = 6, scale = 5)
    private BigDecimal lateProbabilityAfter;

    @Column(name = "risk_score", nullable = false, precision = 7, scale = 4)
    private BigDecimal riskScore;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "factor_contribution_json", nullable = false, columnDefinition = "TEXT")
    private String factorContributionJson;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Version
    @Column(nullable = false)
    private long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public Instant getBaselineAt() { return baselineAt; }
    public void setBaselineAt(Instant baselineAt) { this.baselineAt = baselineAt; }
    public Instant getRecommendedAt() { return recommendedAt; }
    public void setRecommendedAt(Instant recommendedAt) { this.recommendedAt = recommendedAt; }
    public Instant getTargetAt() { return targetAt; }
    public void setTargetAt(Instant targetAt) { this.targetAt = targetAt; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public int getDeltaMinutes() { return deltaMinutes; }
    public void setDeltaMinutes(int deltaMinutes) { this.deltaMinutes = deltaMinutes; }
    public BigDecimal getLateProbabilityBefore() { return lateProbabilityBefore; }
    public void setLateProbabilityBefore(BigDecimal lateProbabilityBefore) { this.lateProbabilityBefore = lateProbabilityBefore; }
    public BigDecimal getLateProbabilityAfter() { return lateProbabilityAfter; }
    public void setLateProbabilityAfter(BigDecimal lateProbabilityAfter) { this.lateProbabilityAfter = lateProbabilityAfter; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getFactorContributionJson() { return factorContributionJson; }
    public void setFactorContributionJson(String factorContributionJson) { this.factorContributionJson = factorContributionJson; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
