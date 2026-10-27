package com.wjh.wakewise.domain.behavior;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** SHRINK_V1 收缩估计：纯函数，直接对算法做行为断言。 */
class WakeLatencyEstimatorTest {

    /** scheduledAt 按编号递增（用于"只看最近 N 条"语义），延迟给 latency 分钟。 */
    private WakeLatencyEstimator.Sample s(int i, double latencyMinutes) {
        return new WakeLatencyEstimator.Sample(
                Instant.parse("2026-10-01T00:00:00Z").plus(i, ChronoUnit.MINUTES), latencyMinutes);
    }

    @Test
    void insufficientSamplesKeepPrior() {
        WakeLatencyEstimator.Estimate e = WakeLatencyEstimator.estimate(
                List.of(s(1, 3), s(2, 5), s(3, 6)));
        // 样本不足：返回固定先验（与引擎旧硬编码一致），冷启动行为不变
        assertThat(e.p50Minutes()).isEqualTo(WakeLatencyEstimator.PRIOR_P50_MIN);
        assertThat(e.variance()).isEqualTo(WakeLatencyEstimator.PRIOR_VARIANCE);
        assertThat(e.sampleCount()).isEqualTo(3);
    }

    @Test
    void empiricalQuantileWithShrinkage() {
        // 六个延迟样本 3/5/6/7/9/12：经验 P50=6.5（R-7 插值）、P90=10.5，
        // 收缩 k=5 后 p50=(6*6.5+5*4)/11≈5.36，应落在先验与经验之间
        WakeLatencyEstimator.Estimate e = WakeLatencyEstimator.estimate(
                List.of(s(1, 3), s(2, 5), s(3, 6), s(4, 7), s(5, 9), s(6, 12)));
        assertThat(e.sampleCount()).isEqualTo(6);
        assertThat(e.p50Minutes()).isBetween(WakeLatencyEstimator.PRIOR_P50_MIN, 6.5);
        assertThat(e.p50Minutes()).isCloseTo(59.0 / 11.0, within(1e-9));
    }

    @Test
    void outlierSamplesAreCappedNotDropped() {
        // 120 分钟的病态样本被截断到上限 60，与直接给 60 的结果一致
        WakeLatencyEstimator.Estimate withOutlier = WakeLatencyEstimator.estimate(
                List.of(s(1, 6), s(2, 7), s(3, 5), s(4, 8), s(5, 120)));
        WakeLatencyEstimator.Estimate capped = WakeLatencyEstimator.estimate(
                List.of(s(1, 6), s(2, 7), s(3, 5), s(4, 8), s(5, 60)));
        assertThat(withOutlier.p50Minutes()).isEqualTo(capped.p50Minutes());
        assertThat(withOutlier.variance()).isEqualTo(capped.variance());
    }

    @Test
    void estimateClampedToSanityRange() {
        WakeLatencyEstimator.Estimate e = WakeLatencyEstimator.estimate(
                List.of(s(1, 120), s(2, 125), s(3, 130), s(4, 140), s(5, 150)));
        assertThat(e.p50Minutes()).isLessThanOrEqualTo(30.0);
        assertThat(e.p50Minutes()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void varianceFloorPreventsDegenerateSigma() {
        // 30 条完全一致的样本：收缩后 P90-P50 趋近 0，sigma 下限 0.5 分钟兜底，
        // 防止迟到概率计算退化为阶跃函数
        List<WakeLatencyEstimator.Sample> identical = IntStream.range(0, 30)
                .mapToObj(i -> s(i, 7.0))
                .toList();
        WakeLatencyEstimator.Estimate e = WakeLatencyEstimator.estimate(identical);
        assertThat(e.variance()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void emptySamplesReturnPrior() {
        WakeLatencyEstimator.Estimate e = WakeLatencyEstimator.estimate(List.of());
        assertThat(e.p50Minutes()).isEqualTo(WakeLatencyEstimator.PRIOR_P50_MIN);
        assertThat(e.sampleCount()).isZero();
    }
}
