package com.wjh.wakewise.domain.behavior;

import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.alarm.AlarmRule;
import com.wjh.wakewise.domain.alarm.AlarmRuleRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 行为学习服务：
 * - 起床延迟校准（SHRINK_V1）：样本 dismissedAt - triggeredAt，ALARM_DISMISSED 事务提交后重算；
 * - 准备时长校准（PREP_SHRINK_V1）：样本 (leftHomeAt - dismissedAt) / 该闹钟设定，USER_LEFT_HOME 事务提交后重算。
 *
 * 单一写入点：事件落库后重算（由 DevicePlanController 在事件事务提交后触发，
 * 校准失败只记日志，绝不影响事件链路——"AI 可以失败，闹钟不能"）。
 * 读取侧：DecisionService.compute / MorningContextService 每次现调 priorsFor，
 * 夜间任务与手动评估自然用上最新校准值。
 */
@Service
public class BehaviorLearningService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorLearningService.class);

    private final BehaviorLearningRepository repository;
    private final WakeHistoryRepository wakeHistoryRepository;
    private final AlarmRuleRepository alarmRuleRepository;
    private final int minSamples;
    private final int shrinkageK;
    private final int maxSamples;
    private final double capMinutes;
    private final int prepMinSamples;
    private final int prepShrinkageK;
    private final int prepMaxSamples;
    private final double prepCapMinutes;

    public BehaviorLearningService(BehaviorLearningRepository repository,
                                   WakeHistoryRepository wakeHistoryRepository,
                                   AlarmRuleRepository alarmRuleRepository,
                                   @Value("${wakewise.behavior.min-samples:5}") int minSamples,
                                   @Value("${wakewise.behavior.shrinkage-k:5}") int shrinkageK,
                                   @Value("${wakewise.behavior.max-samples:30}") int maxSamples,
                                   @Value("${wakewise.behavior.max-latency-minutes:60}") double capMinutes,
                                   @Value("${wakewise.behavior.prep-min-samples:5}") int prepMinSamples,
                                   @Value("${wakewise.behavior.prep-shrinkage-k:5}") int prepShrinkageK,
                                   @Value("${wakewise.behavior.prep-max-samples:30}") int prepMaxSamples,
                                   @Value("${wakewise.behavior.max-prep-minutes:180}") double prepCapMinutes) {
        this.repository = repository;
        this.wakeHistoryRepository = wakeHistoryRepository;
        this.alarmRuleRepository = alarmRuleRepository;
        this.minSamples = minSamples;
        this.shrinkageK = shrinkageK;
        this.maxSamples = maxSamples;
        this.capMinutes = capMinutes;
        this.prepMinSamples = prepMinSamples;
        this.prepShrinkageK = prepShrinkageK;
        this.prepMaxSamples = prepMaxSamples;
        this.prepCapMinutes = prepCapMinutes;
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

    /** 重算该用户的准备时长比率估计并 upsert（样本不足时同样写入先验比率，sampleCount 如实记录）。 */
    @Transactional
    public void recalibratePrepDuration(String userId) {
        List<WakeHistory> histories = wakeHistoryRepository
                .findTop50ByUserIdAndDismissedAtIsNotNullAndLeftHomeAtIsNotNullOrderByScheduledAtDesc(userId);
        // 同一闹钟的设定值只查一次；alarm_rule 无历史版本，历史样本按"当前"设定折算（估计器注释已注明）
        Map<String, Integer> prepByAlarm = new HashMap<>();
        List<PrepDurationEstimator.Sample> samples = new ArrayList<>();
        for (WakeHistory h : histories) {
            if (!h.getLeftHomeAt().isAfter(h.getDismissedAt())) {
                continue;
            }
            Integer configured = prepByAlarm.computeIfAbsent(h.getAlarmId(), alarmId ->
                    alarmRuleRepository.findByAlarmId(alarmId)
                            .map(AlarmRule::getPrepDurationMin)
                            .orElse(null));
            if (configured == null) {
                continue;
            }
            samples.add(new PrepDurationEstimator.Sample(h.getScheduledAt(),
                    Duration.between(h.getDismissedAt(), h.getLeftHomeAt()).toSeconds() / 60.0,
                    configured));
        }
        PrepDurationEstimator.Estimate estimate = PrepDurationEstimator.estimate(
                samples, prepMinSamples, prepMaxSamples, prepShrinkageK, prepCapMinutes);

        BehaviorLearning row = repository
                .findByUserIdAndSegmentKeyAndFeatureKey(userId,
                        BehaviorLearning.SEGMENT_ALL, BehaviorLearning.FEATURE_PREP_DURATION)
                .orElseGet(() -> {
                    BehaviorLearning created = new BehaviorLearning();
                    created.setId(Ids.newId());
                    created.setUserId(userId);
                    created.setSegmentKey(BehaviorLearning.SEGMENT_ALL);
                    created.setFeatureKey(BehaviorLearning.FEATURE_PREP_DURATION);
                    return created;
                });
        row.setEstimate(BigDecimal.valueOf(estimate.ratioP50()).setScale(4, RoundingMode.HALF_UP));
        row.setVariance(BigDecimal.valueOf(estimate.variance()).setScale(6, RoundingMode.HALF_UP));
        row.setSampleCount(estimate.sampleCount());
        row.setLastSampleAt(histories.stream()
                .map(WakeHistory::getScheduledAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        row.setAlgorithmVersion(PrepDurationEstimator.ALGORITHM_VERSION);
        repository.save(row);
        log.info("Prep duration calibrated: user={}, n={}, ratioP50={}, variance={}",
                userId, estimate.sampleCount(), estimate.ratioP50(), estimate.variance());
    }

    /** 读取该用户的校准先验；无记录 / 算法版本不匹配 / 数据异常一律回退固定先验。 */
    @Transactional(readOnly = true)
    public DecisionEngine.BehavioralPriors priorsFor(String userId) {
        // 起床延迟行：既有过滤条件不变（版本匹配 + 字段非空，无样本数门槛——保持历史行为）
        DecisionEngine.BehavioralPriors wake = repository
                .findByUserIdAndSegmentKeyAndFeatureKey(userId,
                        BehaviorLearning.SEGMENT_ALL, BehaviorLearning.FEATURE_WAKE_LATENCY)
                .filter(row -> WakeLatencyEstimator.ALGORITHM_VERSION.equals(row.getAlgorithmVersion()))
                .filter(row -> row.getEstimate() != null && row.getVariance() != null)
                .map(row -> {
                    double p50 = row.getEstimate().doubleValue();
                    double p90 = p50 + 1.2816 * Math.sqrt(row.getVariance().doubleValue());
                    return new DecisionEngine.BehavioralPriors(p50, p90, row.getSampleCount());
                })
                .orElse(DecisionEngine.BehavioralPriors.DEFAULTS);

        // 准备时长行：另加样本数门槛——不足即用默认比率 1.0 / 1.25，
        // 引擎输出与固定系数时代逐字节一致（冷启动零感知的结构性保证，非舍入运气）。
        return repository
                .findByUserIdAndSegmentKeyAndFeatureKey(userId,
                        BehaviorLearning.SEGMENT_ALL, BehaviorLearning.FEATURE_PREP_DURATION)
                .filter(row -> PrepDurationEstimator.ALGORITHM_VERSION.equals(row.getAlgorithmVersion()))
                .filter(row -> row.getEstimate() != null && row.getVariance() != null)
                .filter(row -> row.getSampleCount() >= prepMinSamples)
                .map(row -> {
                    double ratioP50 = row.getEstimate().doubleValue();
                    double ratioP90 = ratioP50 + 1.2816 * Math.sqrt(row.getVariance().doubleValue());
                    return new DecisionEngine.BehavioralPriors(
                            wake.wakeLatencyP50Min(), wake.wakeLatencyP90Min(), wake.sampleCount(),
                            ratioP50, ratioP90, row.getSampleCount());
                })
                .orElse(wake);
    }
}
