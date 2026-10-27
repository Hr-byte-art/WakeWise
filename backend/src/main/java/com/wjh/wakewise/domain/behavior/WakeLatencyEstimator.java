package com.wjh.wakewise.domain.behavior;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 起床延迟估计器（SHRINK_V1）：wake_history 真实样本（dismissedAt - triggeredAt）的经验分位数，
 * 与全局先验做收缩混合（shrinkage）——样本越多越相信个人经验，k 个"虚拟先验样本"防小样本过拟合。
 *
 * 纯函数、确定性：同样样本必然同样输出，便于测试与审计。
 * 样本不足（< minSamples）时返回全局先验（P50=4 / P90=8 分钟），
 * 引擎输出与固定先验时代逐字节一致，冷启动对老用户零感知。
 *
 * 样本语义：贪睡时间计入延迟是特性——引擎建模的就是"从闹钟响到真正起床"。
 */
public final class WakeLatencyEstimator {

    public static final String ALGORITHM_VERSION = "SHRINK_V1";

    /** 全局先验，与 DecisionEngine 的固定先验一致。 */
    static final double PRIOR_P50_MIN = 4.0;
    static final double PRIOR_P90_MIN = 8.0;
    private static final double NORMAL_P90_Z = 1.2816;

    /** 先验方差：((P90 - P50) / z90)^2，与引擎的 sigma 换算公式自洽。 */
    static final double PRIOR_VARIANCE = sq((PRIOR_P90_MIN - PRIOR_P50_MIN) / NORMAL_P90_Z);

    /** sigma 下限（分钟）：防止样本高度一致时迟到概率退化为阶跃函数。 */
    private static final double SIGMA_FLOOR_MIN = 0.5;

    public record Sample(Instant scheduledAt, double latencyMinutes) {}

    public record Estimate(double p50Minutes, double variance, int sampleCount) {}

    private WakeLatencyEstimator() {
    }

    /** 默认参数版本（配置见 wakewise.behavior.*）。 */
    public static Estimate estimate(List<Sample> samples) {
        return estimate(samples, 5, 30, 5, 60);
    }

    public static Estimate estimate(List<Sample> samples, int minSamples, int maxSamples,
                                    int shrinkageK, double capMinutes) {
        if (samples == null || samples.isEmpty()) {
            return new Estimate(PRIOR_P50_MIN, PRIOR_VARIANCE, 0);
        }
        // 1) 只看最近的 maxSamples 条，异常延迟截断到 [0, cap]（不丢弃样本）
        List<Double> latencies = samples.stream()
                .sorted(Comparator.comparing(Sample::scheduledAt).reversed())
                .limit(Math.max(1, maxSamples))
                .map(s -> Math.min(Math.max(s.latencyMinutes(), 0.0), capMinutes))
                .toList();
        int n = latencies.size();
        if (n < minSamples) {
            return new Estimate(PRIOR_P50_MIN, PRIOR_VARIANCE, n);
        }

        // 2) 经验分位数（R-7 线性插值）+ 收缩混合
        double empP50 = quantile(latencies, 0.50);
        double empP90 = quantile(latencies, 0.90);
        double p50 = (n * empP50 + shrinkageK * PRIOR_P50_MIN) / (n + shrinkageK);
        double p90 = (n * empP90 + shrinkageK * PRIOR_P90_MIN) / (n + shrinkageK);

        // 3) 方差（sigma 下限保护）+ P50 合理区间
        double sigma = Math.max((p90 - p50) / NORMAL_P90_Z, SIGMA_FLOOR_MIN);
        p50 = Math.min(Math.max(p50, 1.0), 30.0);
        return new Estimate(p50, sigma * sigma, n);
    }

    /** R-7 分位数（与 numpy/pandas 默认一致）：h = (n-1)q，floor/ceil 线性插值。 */
    private static double quantile(List<Double> values, double q) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        double h = (sorted.size() - 1) * q;
        int lo = (int) Math.floor(h);
        int hi = Math.min((int) Math.ceil(h), sorted.size() - 1);
        return sorted.get(lo) + (h - lo) * (sorted.get(hi) - sorted.get(lo));
    }

    private static double sq(double v) {
        return v * v;
    }
}
