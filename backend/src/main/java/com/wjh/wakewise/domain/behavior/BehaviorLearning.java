package com.wjh.wakewise.domain.behavior;

import com.wjh.wakewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 个人行为参数估计（表结构 V1 已建）。
 * 一期仅使用 featureKey=WAKE_LATENCY / segmentKey=ALL，由 SHRINK_V1 收缩估计算法写入：
 * estimate 存 P50（分钟），variance 存分钟^2，P90 = estimate + 1.2816 * sqrt(variance)。
 */
@Entity
@Table(name = "behavior_learning")
public class BehaviorLearning extends BaseEntity {

    public static final String SEGMENT_ALL = "ALL";
    public static final String FEATURE_WAKE_LATENCY = "WAKE_LATENCY";

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "segment_key", nullable = false, length = 100)
    private String segmentKey;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal estimate;

    @Column(precision = 14, scale = 6)
    private BigDecimal variance;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "last_sample_at")
    private Instant lastSampleAt;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSegmentKey() { return segmentKey; }
    public void setSegmentKey(String segmentKey) { this.segmentKey = segmentKey; }
    public String getFeatureKey() { return featureKey; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public BigDecimal getEstimate() { return estimate; }
    public void setEstimate(BigDecimal estimate) { this.estimate = estimate; }
    public BigDecimal getVariance() { return variance; }
    public void setVariance(BigDecimal variance) { this.variance = variance; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public Instant getLastSampleAt() { return lastSampleAt; }
    public void setLastSampleAt(Instant lastSampleAt) { this.lastSampleAt = lastSampleAt; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
}
