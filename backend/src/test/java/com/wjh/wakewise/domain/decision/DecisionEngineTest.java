package com.wjh.wakewise.domain.decision;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRule;
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
}
