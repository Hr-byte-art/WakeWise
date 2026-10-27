package com.wjh.wakewise.domain.behavior;

import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.decision.DecisionEngine;
import com.wjh.wakewise.domain.history.WakeHistory;
import com.wjh.wakewise.domain.history.WakeHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 行为学习服务：从 wake_history 的真实起床样本（dismissedAt - triggeredAt）校准起床延迟先验。
 *
 * 单一写入点：仅在 ALARM_DISMISSED 事件落库后重算（由 DevicePlanController 在事件事务提交后触发，
 * 校准失败只记日志，绝不影响事件链路——"AI 可以失败，闹钟不能"）。
 * 读取侧：DecisionService.compute / MorningContextService 每次现调 priorsFor，
 * 夜间任务与手动评估自然用上最新校准值。
 */
@Service
public class BehaviorLearningService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorLearningService.class);

    private final BehaviorLearningRepository repository;
    private final WakeHistoryRepository wakeHistoryRepository;
    private final int minSamples;
    private final int shrinkageK;
    private final int maxSamples;
    private final double capMinutes;

    public BehaviorLearningService(BehaviorLearningRepository repository,
                                   WakeHistoryRepository wakeHistoryRepository,
                                   @Value("${wakewise.behavior.min-samples:5}") int minSamples,
                                   @Value("${wakewise.behavior.shrinkage-k:5}") int shrinkageK,
                                   @Value("${wakewise.behavior.max-samples:30}") int maxSamples,
                                   @Value("${wakewise.behavior.max-latency-minutes:60}") double capMinutes) {
        this.repository = repository;
        this.wakeHistoryRepository = wakeHistoryRepository;
        this.minSamples = minSamples;
        this.shrinkageK = shrinkageK;
        this.maxSamples = maxSamples;
        this.capMinutes = capMinutes;
    }

    /** 重算该用户的起床延迟估计并 upsert（样本不足时同样写入先验值，sampleCount 如实记录）。 */
    @Transactional
    public void recalibrateWakeLatency(String userId) {
        List<WakeHistory> histories = wakeHistoryRepository
                .findTop50ByUserIdAndTriggeredAtIsNotNullAndDismissedAtIsNotNullOrderByScheduledAtDesc(userId);
        List<WakeLatencyEstimator.Sample> samples = histories.stream()
                .filter(h -> h.getDismissedAt().isAfter(h.getTriggeredAt()))
                .map(h -> new WakeLatencyEstimator.Sample(h.getScheduledAt(),
                        Duration.between(h.getTriggeredAt(), h.getDismissedAt()).toSeconds() / 60.0))
                .toList();
        WakeLatencyEstimator.Estimate estimate = WakeLatencyEstimator.estimate(
                samples, minSamples, maxSamples, shrinkageK, capMinutes);

        BehaviorLearning row = repository
                .findByUserIdAndSegmentKeyAndFeatureKey(userId,
                        BehaviorLearning.SEGMENT_ALL, BehaviorLearning.FEATURE_WAKE_LATENCY)
                .orElseGet(() -> {
                    BehaviorLearning created = new BehaviorLearning();
                    created.setId(Ids.newId());
                    created.setUserId(userId);
                    created.setSegmentKey(BehaviorLearning.SEGMENT_ALL);
                    created.setFeatureKey(BehaviorLearning.FEATURE_WAKE_LATENCY);
                    return created;
                });
        row.setEstimate(BigDecimal.valueOf(estimate.p50Minutes()).setScale(4, RoundingMode.HALF_UP));
        row.setVariance(BigDecimal.valueOf(estimate.variance()).setScale(6, RoundingMode.HALF_UP));
        row.setSampleCount(estimate.sampleCount());
        row.setLastSampleAt(histories.stream()
                .map(WakeHistory::getScheduledAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        row.setAlgorithmVersion(WakeLatencyEstimator.ALGORITHM_VERSION);
        repository.save(row);
        log.info("Wake latency calibrated: user={}, n={}, p50={}min, variance={}",
                userId, estimate.sampleCount(), estimate.p50Minutes(), estimate.variance());
    }

    /** 读取该用户的校准先验；无记录 / 算法版本不匹配 / 数据异常一律回退固定先验。 */
    @Transactional(readOnly = true)
    public DecisionEngine.BehavioralPriors priorsFor(String userId) {
        return repository.findByUserIdAndSegmentKeyAndFeatureKey(userId,
                        BehaviorLearning.SEGMENT_ALL, BehaviorLearning.FEATURE_WAKE_LATENCY)
                .filter(row -> WakeLatencyEstimator.ALGORITHM_VERSION.equals(row.getAlgorithmVersion()))
                .filter(row -> row.getEstimate() != null && row.getVariance() != null)
                .map(row -> {
                    double p50 = row.getEstimate().doubleValue();
                    double p90 = p50 + 1.2816 * Math.sqrt(row.getVariance().doubleValue());
                    return new DecisionEngine.BehavioralPriors(p50, p90, row.getSampleCount());
                })
                .orElse(DecisionEngine.BehavioralPriors.DEFAULTS);
    }
}
