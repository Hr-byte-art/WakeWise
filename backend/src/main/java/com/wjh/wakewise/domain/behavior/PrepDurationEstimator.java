package com.wjh.wakewise.domain.behavior;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 准备时长估计器（PREP_SHRINK_V1）：wake_history 出门样本（leftHomeAt - dismissedAt）相对
 * 该样本闹钟用户设定 prepDurationMin 的比率，与先验比率 1.0 做收缩混合（shrinkage）——
 * 样本越多越相信真实出门数据，k 个"虚拟先验样本"防小样本过拟合。
 *
 * 为什么是比率而非绝对分钟：准备时长强依赖个人设定（5 分钟还是 60 分钟），绝对先验无意义；
 * 先验比率 1.0 = "相信用户设置是对的"，校准学的是"实际是设定的几倍"。P90 先验 1.25 与
 * DecisionEngine.PREP_P90_FACTOR 自洽，样本不足时引擎输出与固定系数时代逐字节一致，冷启动零感知。
 *
 * 已知限制：alarm_rule 无历史版本，历史样本按闹钟"当前"设定折算；max-samples 的近期窗口
 * 缓解用户大幅调整设定后的失真。
 *
 * 纯函数、确定性：同样样本必然同样输出，便于测试与审计。
 */
public final class PrepDurationEstimator {

    public static final String ALGORITHM_VERSION = "PREP_SHRINK_V1";

    /** 先验比率，与 DecisionEngine 的固定 prep 系数一致（1.0 / 1.25）。 */
    static final double PRIOR_RATIO_P50 = 1.0;
    static final double PRIOR_RATIO_P90 = 1.25;
    private static final double NORMAL_P90_Z = 1.2816;

    /** 先验方差：((P90 - P50) / z90)^2，与引擎的 sigma 换算公式自洽。 */
    static final double PRIOR_RATIO_VARIANCE = sq((PRIOR_RATIO_P90 - PRIOR_RATIO_P50) / NORMAL_P90_Z);

    /** sigma 下限：默认参数下收缩最小 sigma≈0.0278，取 0.03 让其在高一致样本时恰好 bind。 */
    private static final double RATIO_SIGMA_FLOOR = 0.03;

    /** 比率合理区间：脏数据/时钟异常对校准结果的爆炸半径钳在设定的 [0.5, 2.0] 倍。 */
    static final double RATIO_MIN = 0.5;
    static final double RATIO_MAX = 2.0;

    /** scheduledAt 仅用于"只看最近 maxSamples 条"；actualMinutes = 出门 - 关闭闹钟；configuredPrepMin = 该闹钟当前设定。 */
    public record Sample(Instant scheduledAt, double actualMinutes, int configuredPrepMin) {}

    /** estimate = 比率 P50（无量纲），variance = 比率方差，sampleCount = 可算比率的有效样本数。 */
    public record Estimate(double ratioP50, double variance, int sampleCount) {}

    private PrepDurationEstimator() {
    }

    /** 默认参数版本（配置见 wakewise.behavior.prep-*）。 */
    public static Estimate estimate(List<Sample> samples) {
        return estimate(samples, 5, 30, 5, 180);
    }

    public static Estimate estimate(List<Sample> samples, int minSamples, int maxSamples,
                                    int shrinkageK, double capMinutes) {
        if (samples == null || samples.isEmpty()) {
            return new Estimate(PRIOR_RATIO_P50, PRIOR_RATIO_VARIANCE, 0);
        }
        // 1) 设定值 <= 0 的样本比率无意义，整条剔除（不计入 n）；
        //    其余只看最近的 maxSamples 条，异常实际分钟截断到 [0, cap]（不丢弃样本）
        List<Double> ratios = samples.stream()
                .filter(s -> s.configuredPrepMin() > 0)
                .sorted(Comparator.comparing(Sample::scheduledAt).reversed())
                .limit(Math.max(1, maxSamples))
                .map(s -> Math.min(Math.max(s.actualMinutes(), 0.0), capMinutes) / s.configuredPrepMin())
                .toList();
        int n = ratios.size();
        if (n < minSamples) {
            return new Estimate(PRIOR_RATIO_P50, PRIOR_RATIO_VARIANCE, n);
        }

        // 2) 经验分位数（R-7 线性插值）+ 收缩混合（向"信任用户设置"的先验 1.0 / 1.25）
        double empP50 = quantile(ratios, 0.50);
        double empP90 = quantile(ratios, 0.90);
        double ratioP50 = (n * empP50 + shrinkageK * PRIOR_RATIO_P50) / (n + shrinkageK);
        double ratioP90 = (n * empP90 + shrinkageK * PRIOR_RATIO_P90) / (n + shrinkageK);

        // 3) 方差（sigma 下限保护）+ 比率合理区间
        double sigma = Math.max((ratioP90 - ratioP50) / NORMAL_P90_Z, RATIO_SIGMA_FLOOR);
        ratioP50 = Math.min(Math.max(ratioP50, RATIO_MIN), RATIO_MAX);
        return new Estimate(ratioP50, sigma * sigma, n);
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
