package com.wjh.backend.domain.decision;

import java.math.BigDecimal;
import java.util.List;

/**
 * 天气因子加权计算结果
 */
public record WeatherFactorResult(
        BigDecimal compositeCoefficient,  // 综合天气系数 1.0~2.5
        List<FactorContribution> contributions
) {
    public record FactorContribution(
            String factorCode,     // 因子代码: PRECIP, VISIBILITY, TEMP, WIND, AQI, SUNRISE
            String factorName,     // 因子名称
            double weight,         // 权重
            double intensity,      // 强度 1.0~2.0
            double impact          // 贡献值 = weight × (intensity - 1.0)
    ) {}
}