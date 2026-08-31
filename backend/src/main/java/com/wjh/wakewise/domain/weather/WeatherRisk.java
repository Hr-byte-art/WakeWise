package com.wjh.wakewise.domain.weather;

/** 决策引擎消费的晨间天气风险摘要。available=false 表示数据不可用（降级路径）。 */
public record WeatherRisk(
        boolean available,
        String level,
        double precipProbability,
        double precipMm,
        double temperatureC,
        Integer weatherCode,
        String alertLevel,
        double qualityScore) {

    public static final String LEVEL_NONE = "NONE";
    public static final String LEVEL_LIGHT = "LIGHT";
    public static final String LEVEL_MODERATE = "MODERATE";
    public static final String LEVEL_HEAVY = "HEAVY";

    public static WeatherRisk unavailable() {
        return new WeatherRisk(false, LEVEL_NONE, 0, 0, 0, null, null, 0.0);
    }

    public boolean heavy() {
        return available && (precipProbability >= 0.60 || precipMm >= 5.0);
    }

    public boolean moderate() {
        return available && !heavy() && (precipProbability >= 0.30 || precipMm >= 1.5);
    }

    public boolean light() {
        return available && !heavy() && !moderate() && (precipProbability >= 0.05 || precipMm >= 0.2);
    }

    public boolean severeAlert() {
        return available && weatherCode != null && (weatherCode >= 95 || (weatherCode >= 65 && weatherCode < 70));
    }
}
