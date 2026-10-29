package com.wjh.wakewise.domain.behavior;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** PREP_SHRINK_V1 比率收缩估计：纯函数，直接对算法做行为断言。 */
class PrepDurationEstimatorTest {

    /** scheduledAt 按编号递增（用于"只看最近 N 条"语义），实际分钟给 actual，设定值统一 prep。 */
    private PrepDurationEstimator.Sample s(int i, double actualMinutes) {
        return s(i, actualMinutes, 30);
    }

    private PrepDurationEstimator.Sample s(int i, double actualMinutes, int configuredPrepMin) {
        return new PrepDurationEstimator.Sample(
                Instant.parse("2026-10-01T00:00:00Z").plus(i, ChronoUnit.MINUTES),
                actualMinutes, configuredPrepMin);
    }

    @Test
    void insufficientSamplesKeepPriorRatio() {
        PrepDurationEstimator.Estimate e = PrepDurationEstimator.estimate(
                List.of(s(1, 36), s(2, 42), s(3, 48)));
        // 样本不足：比率回先验 1.0（引擎输出与固定系数时代一致），样本数如实
        assertThat(e.ratioP50()).isEqualTo(PrepDurationEstimator.PRIOR_RATIO_P50);
        assertThat(e.variance()).isEqualTo(PrepDurationEstimator.PRIOR_RATIO_VARIANCE);
        assertThat(e.sampleCount()).isEqualTo(3);
    }

    @Test
    void empiricalQuantileWithShrinkage() {
        // 设定 30、实际 36/42/48/54/60/72 → 比率 1.2/1.4/1.6/1.8/2.0/2.4；
        // 经验 P50=1.7（R-7 插值），收缩 k=5 后 r50=(6*1.7+5*1.0)/11=15.2/11
        PrepDurationEstimator.Estimate e = PrepDurationEstimator.estimate(
                List.of(s(1, 36), s(2, 42), s(3, 48), s(4, 54), s(5, 60), s(6, 72)));
        assertThat(e.sampleCount()).isEqualTo(6);
        assertThat(e.ratioP50()).isBetween(PrepDurationEstimator.PRIOR_RATIO_P50, 1.7);
        assertThat(e.ratioP50()).isCloseTo(15.2 / 11.0, within(1e-9));
    }

    @Test
    void outlierActualMinutesCappedNotDropped() {
        // 实际 300 分钟的病态样本截断到上限 180，与直接给 180 的结果一致
        PrepDurationEstimator.Estimate withOutlier = PrepDurationEstimator.estimate(
                List.of(s(1, 36), s(2, 42), s(3, 48), s(4, 54), s(5, 300)));
        PrepDurationEstimator.Estimate capped = PrepDurationEstimator.estimate(
                List.of(s(1, 36), s(2, 42), s(3, 48), s(4, 54), s(5, 180)));
        assertThat(withOutlier.ratioP50()).isEqualTo(capped.ratioP50());
        assertThat(withOutlier.variance()).isEqualTo(capped.variance());
    }

    @Test
    void nonPositivePrepSamplesExcludedFromCount() {
        // 设定值 <= 0 的样本组不出比率：剔除且不计入样本数
        PrepDurationEstimator.Estimate withZeros = PrepDurationEstimator.estimate(List.of(
                s(1, 36), s(2, 42), s(3, 48), s(4, 54), s(5, 60), s(6, 72),
                s(7, 10, 0), s(8, 10, 0)));
        PrepDurationEstimator.Estimate clean = PrepDurationEstimator.estimate(
                List.of(s(1, 36), s(2, 42), s(3, 48), s(4, 54), s(5, 60), s(6, 72)));
        assertThat(withZeros.sampleCount()).isEqualTo(6);
        assertThat(withZeros.ratioP50()).isEqualTo(clean.ratioP50());
        assertThat(withZeros.variance()).isEqualTo(clean.variance());
    }

    @Test
    void estimateClampedToSanityRange() {
        // 比率 100/30≈3.33 的脏样本：收缩后仍被夹进 [0.5, 2.0]
        PrepDurationEstimator.Estimate e = PrepDurationEstimator.estimate(
                List.of(s(1, 100), s(2, 100), s(3, 100), s(4, 100), s(5, 100)));
        assertThat(e.ratioP50()).isLessThanOrEqualTo(PrepDurationEstimator.RATIO_MAX);
        assertThat(e.ratioP50()).isGreaterThanOrEqualTo(PrepDurationEstimator.RATIO_MIN);
    }

    @Test
    void varianceFloorBindsWhenSamplesUltraConsistent() {
        // 30 条完全一致的比率：收缩后 P90-P50 = 5*0.25/35 ≈ 0.0357，sigma≈0.0279 < 0.03 下限兜底
        List<PrepDurationEstimator.Sample> identical = IntStream.range(0, 30)
                .mapToObj(i -> s(i, 42.0))
                .toList();
        PrepDurationEstimator.Estimate e = PrepDurationEstimator.estimate(identical);
        assertThat(e.variance()).isCloseTo(0.03 * 0.03, within(1e-9));
    }

    @Test
    void emptySamplesReturnPrior() {
        PrepDurationEstimator.Estimate e = PrepDurationEstimator.estimate(List.of());
        assertThat(e.ratioP50()).isEqualTo(PrepDurationEstimator.PRIOR_RATIO_P50);
        assertThat(e.sampleCount()).isZero();
    }
}
