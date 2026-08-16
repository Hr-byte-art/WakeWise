package com.wjh.backend.domain.decision;

import com.wjh.backend.domain.weather.WeatherCache;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 天气因子加权计算器
 * 根据缓存的天气数据，计算综合天气影响系数和各因子贡献
 */
@Component
public class WeatherFactorCalculator {

    // 因子权重配置
    private static final double W_PRECIP = 0.30;
    private static final double W_VISIBILITY = 0.20;
    private static final double W_TEMP = 0.15;
    private static final double W_WIND = 0.10;
    private static final double W_AQI = 0.10;
    private static final double W_SUNRISE = 0.15;

    /**
     * 计算综合天气系数
     * @return 系数范围 1.0 ~ 2.5，1.0 表示天气无影响
     */
    public WeatherFactorResult calculate(WeatherCache weather) {
        List<WeatherFactorResult.FactorContribution> contributions = new ArrayList<>();

        double precipIntensity = calcPrecipIntensity(weather);
        double visIntensity = calcVisibilityIntensity(weather);
        double tempIntensity = calcTempIntensity(weather);
        double windIntensity = calcWindIntensity(weather);
        double aqiIntensity = calcAqiIntensity(weather);
        double sunriseIntensity = 1.0; // MVP 暂不接入日出数据，默认无影响

        contributions.add(contribution("PRECIP", "降水", W_PRECIP, precipIntensity));
        contributions.add(contribution("VISIBILITY", "能见度", W_VISIBILITY, visIntensity));
        contributions.add(contribution("TEMP", "极端温度", W_TEMP, tempIntensity));
        contributions.add(contribution("WIND", "风力", W_WIND, windIntensity));
        contributions.add(contribution("AQI", "空气质量", W_AQI, aqiIntensity));
        contributions.add(contribution("SUNRISE", "日出时间", W_SUNRISE, sunriseIntensity));

        // 综合系数 = 1.0 + Σ(weight × (intensity - 1.0))
        double totalImpact = contributions.stream()
                .mapToDouble(c -> c.weight() * (c.intensity() - 1.0))
                .sum();
        double coefficient = 1.0 + totalImpact;

        // 裁剪到 [1.0, 2.5]
        coefficient = Math.max(1.0, Math.min(2.5, coefficient));

        return new WeatherFactorResult(
                BigDecimal.valueOf(coefficient).setScale(4, RoundingMode.HALF_UP),
                contributions
        );
    }

    /**
     * 无天气数据时的默认结果
     */
    public WeatherFactorResult neutral() {
        List<WeatherFactorResult.FactorContribution> contributions = List.of(
                contribution("PRECIP", "降水", W_PRECIP, 1.0),
                contribution("VISIBILITY", "能见度", W_VISIBILITY, 1.0),
                contribution("TEMP", "极端温度", W_TEMP, 1.0),
                contribution("WIND", "风力", W_WIND, 1.0),
                contribution("AQI", "空气质量", W_AQI, 1.0),
                contribution("SUNRISE", "日出时间", W_SUNRISE, 1.0)
        );
        return new WeatherFactorResult(BigDecimal.ONE, contributions);
    }

    // --- 各因子强度计算 ---

    private double calcPrecipIntensity(WeatherCache w) {
        if (w.getPrecipProbability() == null && w.getPrecipMm() == null) return 1.0;
        double prob = w.getPrecipProbability() != null ? w.getPrecipProbability().doubleValue() : 0;
        double mm = w.getPrecipMm() != null ? w.getPrecipMm().doubleValue() : 0;

        // 降水概率 > 50% 或降水量 > 1mm 开始有影响
        if (prob < 0.3 && mm < 0.5) return 1.0;
        if (prob < 0.5 && mm < 2.0) return 1.2;
        if (prob < 0.7 && mm < 5.0) return 1.4;
        if (prob < 0.9 && mm < 10.0) return 1.6;
        if (mm < 25.0) return 1.8;
        return 2.0; // 暴雨
    }

    private double calcVisibilityIntensity(WeatherCache w) {
        if (w.getVisibilityM() == null) return 1.0;
        int vis = w.getVisibilityM();
        if (vis > 5000) return 1.0;
        if (vis > 2000) return 1.2;
        if (vis > 1000) return 1.4;
        if (vis > 500) return 1.6;
        return 1.8; // 浓雾
    }

    private double calcTempIntensity(WeatherCache w) {
        if (w.getTemperatureC() == null) return 1.0;
        double temp = w.getTemperatureC().doubleValue();
        if (temp > 5 && temp < 30) return 1.0;
        if (temp <= 5 && temp > -5) return 1.2;
        if (temp <= -5) return 1.4;
        if (temp >= 30 && temp < 35) return 1.2;
        if (temp >= 35 && temp < 38) return 1.3;
        return 1.5; // 极端高温
    }

    private double calcWindIntensity(WeatherCache w) {
        if (w.getWindSpeedMps() == null) return 1.0;
        double wind = w.getWindSpeedMps().doubleValue();
        if (wind < 5.5) return 1.0;   // < 4级
        if (wind < 8.0) return 1.1;   // 4-5级
        if (wind < 10.8) return 1.2;  // 5-6级
        return 1.3;                    // 6级以上
    }

    private double calcAqiIntensity(WeatherCache w) {
        if (w.getAqi() == null) return 1.0;
        int aqi = w.getAqi();
        if (aqi <= 50) return 1.0;
        if (aqi <= 100) return 1.05;
        if (aqi <= 150) return 1.1;
        if (aqi <= 200) return 1.15;
        return 1.2; // 重度污染
    }

    private WeatherFactorResult.FactorContribution contribution(
            String code, String name, double weight, double intensity) {
        double impact = weight * (intensity - 1.0);
        return new WeatherFactorResult.FactorContribution(code, name, weight, intensity, impact);
    }
}