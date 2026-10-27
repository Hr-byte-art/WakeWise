package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRule;
import com.wjh.wakewise.domain.travel.TravelTime;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine(5, 8, 3);

    private Alarm alarm(Alarm.TravelMode mode) {
        Alarm alarm = new Alarm();
        alarm.setId("01ALARM");
        alarm.setTargetType(Alarm.AlarmType.ARRIVE_BY);
        alarm.setTravelMode(mode);
        alarm.setDestinationLabel("公司");
        return alarm;
    }

    private AlarmRule rule(int commuteMinutes) {
        AlarmRule rule = new AlarmRule();
        rule.setAlarmId("01ALARM");
        rule.setPrepDurationMin(30);
        rule.setCommuteMinutes(commuteMinutes);
        rule.setMaxAdvanceMin(30);
        rule.setMaxDelayMin(10);
        rule.setAllowAutoDelay(false);
        rule.setAutomationLevel(1);
        return rule;
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId("01USER");
        user.setRiskPreference(UserAccount.RiskPreference.BALANCED);
        user.setTimezone("Asia/Shanghai");
        return user;
    }

    /** 目标 08:45 到达（UTC 00:45），公交通勤 40 分钟。 */
    private final Instant target = Instant.parse("2026-08-04T00:45:00Z");

    @Test
    void rainyDayRecommendsEarlierThanClearDay() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.05, 0, 26, 0, null, 0.95);
        WeatherRisk storm = new WeatherRisk(true, WeatherRisk.LEVEL_HEAVY, 0.85, 12, 21, 65, "WARNING", 0.95);

        WakePlan clearPlan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), clear, target, null);
        WakePlan stormPlan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), storm, target, null);

        // 暴雨日至少和晴天一样早，且确实提前于基准
        assertThat(stormPlan.recommendedWakeAt()).isBeforeOrEqualTo(clearPlan.recommendedWakeAt());
        assertThat(stormPlan.deltaMinutes()).isNegative();
        // 不允许越过 maxAdvance = 30 分钟的边界
        assertThat(ChronoUnit.MINUTES.between(stormPlan.recommendedWakeAt(), stormPlan.baselineWakeAt()))
                .isLessThanOrEqualTo(30);
        // 暴雨日的迟到概率应不高于基准计划
        assertThat(stormPlan.lateProbabilityAfter()).isLessThanOrEqualTo(stormPlan.lateProbabilityBefore());
        // 提前量应有可解释因素
        assertThat(stormPlan.topFactors()).isNotEmpty();
        assertThat(stormPlan.reasonCode()).isIn("WEATHER_TRAIL_INCREASE", "ADVANCE_CAPPED");
    }

    @Test
    void clearDayWithSmallMarginKeepsBaseline() {
        // 睡眠优先 + 无通勤 + 短准备：晴天时尾部余量不足 10 分钟，不值得折腾
        UserAccount sleepFirst = user();
        sleepFirst.setRiskPreference(UserAccount.RiskPreference.SLEEP_FIRST);
        AlarmRule rule = rule(0);
        rule.setPrepDurationMin(20);
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        WakePlan plan = engine.evaluate(alarm(Alarm.TravelMode.WALK), rule, sleepFirst, clear, target, null);

        assertThat(plan.deltaMinutes()).isZero();
        assertThat(plan.reasonCode()).isEqualTo("KEEP_BASELINE");
        assertThat(plan.recommendedWakeAt()).isEqualTo(plan.baselineWakeAt());
    }

    @Test
    void delayIsNotAllowedWithoutPermission() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        AlarmRule rule = rule(40);
        rule.setAllowAutoDelay(false);

        WakePlan plan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule, user(), clear, target, null);

        assertThat(plan.recommendedWakeAt()).isBeforeOrEqualTo(plan.baselineWakeAt());
    }

    @Test
    void conservativePreferenceNeverEarlierThanAllowed() {
        UserAccount conservative = user();
        conservative.setRiskPreference(UserAccount.RiskPreference.CONSERVATIVE);
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);

        WakePlan plan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), conservative, clear, target, null);

        assertThat(ChronoUnit.MINUTES.between(plan.recommendedWakeAt(), plan.baselineWakeAt()))
                .isLessThanOrEqualTo(30);
        // 保守型应比平衡型更早（或持平）
        WakePlan balanced = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), clear, target, null);
        assertThat(plan.recommendedWakeAt()).isBeforeOrEqualTo(balanced.recommendedWakeAt());
    }

    // ------------------------------------------------------------------ 行为先验校准

    @Test
    void calibratedPriorsShiftBaseline() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        AlarmRule rule = rule(40);
        DecisionEngine.BehavioralPriors slowUser = new DecisionEngine.BehavioralPriors(10, 16, 12);

        // 慢起用户（P50=10）的基准唤醒应比默认（P50=4）早 6 分钟
        Instant defaultBaseline = engine.baselineWakeAt(rule, target);
        Instant slowBaseline = engine.baselineWakeAt(rule, target, slowUser);
        assertThat(ChronoUnit.MINUTES.between(slowBaseline, defaultBaseline)).isEqualTo(6);

        // 需要更多起床时间的用户，推荐唤醒不应比默认更晚
        WakePlan defaultPlan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule, user(), clear, target, null);
        WakePlan slowPlan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule, user(), clear, target, null, slowUser);
        assertThat(slowPlan.recommendedWakeAt()).isBeforeOrEqualTo(defaultPlan.recommendedWakeAt());
    }

    @Test
    void modelVersionAnnotatesCalibration() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        WakePlan calibrated = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), clear, target, null,
                new DecisionEngine.BehavioralPriors(4, 8, 12));
        WakePlan defaults = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), clear, target, null);

        assertThat(calibrated.modelVersion()).isEqualTo("QUANTILE_V1+CAL(n=12)");
        assertThat(defaults.modelVersion()).isEqualTo("QUANTILE_V1");
    }

    @Test
    void leaveByUsesCalibratedPriors() {
        AlarmRule rule = rule(40);
        Instant wake = Instant.parse("2026-08-03T23:00:00Z");
        // 校准 P90=12 比默认 8 晚 4 分钟，建议出门时间应相应推迟 4 分钟
        Instant defaultLeave = engine.leaveBy(rule, wake);
        Instant calibratedLeave = engine.leaveBy(rule, wake,
                new DecisionEngine.BehavioralPriors(4, 12, 10));
        assertThat(ChronoUnit.MINUTES.between(defaultLeave, calibratedLeave)).isEqualTo(4);
    }

    // ------------------------------------------------------------------ 真实交通 ETA

    @Test
    void realTravelReplacesManualCommuteAndShiftsBaseline() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        Alarm alarm = alarm(Alarm.TravelMode.TRANSIT);
        UserAccount u = user();
        TravelTime slower = new TravelTime(true, 52, 0.90); // 高德说 52 分钟，手填 40

        WakePlan manual = engine.evaluate(alarm, rule(40), u, clear, target, null);
        WakePlan real = engine.evaluate(alarm, rule(40), u, clear, target, null,
                DecisionEngine.BehavioralPriors.DEFAULTS, slower);

        // 基准/理想唤醒整体前移 12 分钟（52-40），modelVersion 标注 +ETA
        assertThat(ChronoUnit.MINUTES.between(real.baselineWakeAt(), manual.baselineWakeAt())).isEqualTo(12);
        assertThat(real.modelVersion()).isEqualTo("QUANTILE_V1+ETA");
        assertThat(real.recommendedWakeAt()).isBeforeOrEqualTo(manual.recommendedWakeAt());
    }

    @Test
    void unavailableTravelBehavesExactlyLikeNoTravel() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        Alarm alarm = alarm(Alarm.TravelMode.TRANSIT);
        UserAccount u = user();

        WakePlan without = engine.evaluate(alarm, rule(40), u, clear, target, null);
        WakePlan withUnavailable = engine.evaluate(alarm, rule(40), u, clear, target, null,
                DecisionEngine.BehavioralPriors.DEFAULTS, TravelTime.unavailable());
        WakePlan withNull = engine.evaluate(alarm, rule(40), u, clear, target, null,
                DecisionEngine.BehavioralPriors.DEFAULTS, null);

        // 不可用/未传逐字节一致：ETA 失败对旧行为零感知（确定性锚定）
        assertThat(withUnavailable.modelVersion()).isEqualTo("QUANTILE_V1");
        assertThat(withUnavailable.baselineWakeAt()).isEqualTo(without.baselineWakeAt());
        assertThat(withUnavailable.recommendedWakeAt()).isEqualTo(without.recommendedWakeAt());
        assertThat(withNull.recommendedWakeAt()).isEqualTo(without.recommendedWakeAt());
    }

    @Test
    void modelVersionCombinesCalibrationAndEta() {
        WeatherRisk clear = new WeatherRisk(true, WeatherRisk.LEVEL_NONE, 0.02, 0, 26, 0, null, 0.95);
        WakePlan plan = engine.evaluate(alarm(Alarm.TravelMode.TRANSIT), rule(40), user(), clear, target, null,
                new DecisionEngine.BehavioralPriors(4, 8, 6), new TravelTime(true, 40, 0.90));
        assertThat(plan.modelVersion()).isEqualTo("QUANTILE_V1+CAL(n=6)+ETA");
    }

    @Test
    void baselineWakeAtAcceptsRealTravelOverride() {
        AlarmRule rule = rule(40);
        // 手填 40 → 真实 25：基准唤醒应晚 15 分钟（requiredMean 更小）
        Instant manual = engine.baselineWakeAt(rule, target, DecisionEngine.BehavioralPriors.DEFAULTS);
        Instant real = engine.baselineWakeAt(rule, target, DecisionEngine.BehavioralPriors.DEFAULTS, 25);
        assertThat(ChronoUnit.MINUTES.between(manual, real)).isEqualTo(15);
    }
}
