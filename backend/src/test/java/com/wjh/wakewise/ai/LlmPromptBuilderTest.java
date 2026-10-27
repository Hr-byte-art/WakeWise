package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 提示词组装：纯函数，锚定"事实只来自计划、红线措辞齐全"。 */
class LlmPromptBuilderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant TARGET = Instant.parse("2026-10-28T00:45:00Z");     // 08:45
    private static final Instant BASELINE = Instant.parse("2026-10-27T23:10:00Z");  // 07:10
    private static final Instant RECOMMENDED = Instant.parse("2026-10-27T22:55:00Z"); // 06:55

    private WakePlan plan(int deltaMinutes) {
        return new WakePlan(null, "alarm-1", null, BASELINE, RECOMMENDED, deltaMinutes,
                TARGET, 0.20, 0.05, 12.0, 0.82,
                List.of(new WakePlan.Factor("TRAVEL_TAIL_RISK", 20, "通勤时长波动余量")),
                "RISK_QUANTILE_MARGIN", null, false, "QUANTILE_V1+CAL(n=6)");
    }

    private Alarm alarm() {
        Alarm alarm = new Alarm();
        alarm.setName("上班");
        alarm.setDestinationLabel("公司");
        alarm.setTravelMode(Alarm.TravelMode.TRANSIT);
        return alarm;
    }

    @Test
    void systemPromptStatesRedLines() {
        String prompt = LlmPromptBuilder.systemPrompt(300);
        // 红线：禁止编造/推算/更改数字、不算时间、限长、禁 Markdown
        assertThat(prompt).contains("禁止编造、推算或更改任何时间与数字");
        assertThat(prompt).contains("不参与任何时间计算");
        assertThat(prompt).contains("300 字");
        assertThat(prompt).contains("不使用 Markdown");
    }

    @Test
    void factsContainOnlyPlanTimesAndAttributes() {
        // 概率 0.4 / 雨量 3.2：不足 heavy(>=0.60/>=5.0)、达到 moderate(>=0.30/>=1.5)
        WeatherRisk moderateRain = new WeatherRisk(true, WeatherRisk.LEVEL_MODERATE,
                0.4, 3.2, 18.0, 63, null, 0.9);
        String facts = LlmPromptBuilder.facts(plan(-15), alarm(), ZONE, moderateRain);
        // 全部为引擎已算好的本地时刻事实，LLM 无从也无须自行推算
        assertThat(facts).contains("07:10");
        assertThat(facts).contains("06:55");
        assertThat(facts).contains("08:45");
        assertThat(facts).contains("提前 15 分钟");
        assertThat(facts).contains("目的地「公司」");
        assertThat(facts).contains("TRANSIT");
        assertThat(facts).contains("中雨");
        assertThat(facts).contains("通勤时长波动余量（约 +20 分钟）");
        assertThat(facts).contains("82%");
        assertThat(facts).contains("QUANTILE_V1+CAL(n=6)");
    }

    @Test
    void factsCoverKeepBaselineAndDelayAndUnavailableWeather() {
        String keep = LlmPromptBuilder.facts(plan(0), alarm(), ZONE, WeatherRisk.unavailable());
        assertThat(keep).contains("保持 07:10 不变");
        assertThat(keep).contains("暂无天气数据");

        String delay = LlmPromptBuilder.facts(plan(10), alarm(), ZONE, WeatherRisk.unavailable());
        assertThat(delay).contains("延后 10 分钟");
    }

    @Test
    void factsFallbackDestinationWhenBlank() {
        Alarm noDestination = new Alarm();
        noDestination.setName("上班");
        String facts = LlmPromptBuilder.facts(plan(0), noDestination, ZONE, WeatherRisk.unavailable());
        assertThat(facts).contains("目的地「目的地」");
        assertThat(facts).doesNotContain("出行方式");
    }
}
