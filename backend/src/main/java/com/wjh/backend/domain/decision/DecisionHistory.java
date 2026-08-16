package com.wjh.backend.domain.decision;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "decision_history")
public class DecisionHistory {

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

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionStatus status;

    @Column(name = "risk_score", nullable = false, precision = 7, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "late_probability", nullable = false, precision = 6, scale = 5)
    private BigDecimal lateProbability;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "factor_contribution_json", columnDefinition = "JSON", nullable = false)
    private String factorContributionJson;

    @Column(name = "context_snapshot_json", columnDefinition = "JSON", nullable = false)
    private String contextSnapshotJson;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public enum DecisionStatus { PROPOSED, PENDING, ARMED, REVERTED, FAILED, EXECUTED }

    @PrePersist
    void prePersist() {
        if (id == null) id = UlidCreator.getUlid().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // --- getters / setters ---
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
    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }
    public DecisionStatus getStatus() { return status; }
    public void setStatus(DecisionStatus status) { this.status = status; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public BigDecimal getLateProbability() { return lateProbability; }
    public void setLateProbability(BigDecimal lateProbability) { this.lateProbability = lateProbability; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getFactorContributionJson() { return factorContributionJson; }
    public void setFactorContributionJson(String factorContributionJson) { this.factorContributionJson = factorContributionJson; }
    public String getContextSnapshotJson() { return contextSnapshotJson; }
    public void setContextSnapshotJson(String contextSnapshotJson) { this.contextSnapshotJson = contextSnapshotJson; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}