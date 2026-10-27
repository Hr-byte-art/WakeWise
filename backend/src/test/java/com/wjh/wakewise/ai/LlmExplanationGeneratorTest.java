package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** LLM 解释生成：成功用 LLM 文案，端点未启用/失败/空白一律回落模板，输出必净必截断。 */
class LlmExplanationGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant TARGET = Instant.parse("2026-10-28T00:45:00Z");
    private static final Instant BASELINE = Instant.parse("2026-10-27T23:10:00Z");
    private static final Instant RECOMMENDED = Instant.parse("2026-10-27T22:55:00Z");

    /** 生效端点（调用方解析后传入；测试即用户级 BYOK 生效的场景）。 */
    private static final LlmEndpoint ENDPOINT = LlmEndpoint.of("https://llm.example/v1", "sk-test", "test-model");

    /** 手写小替身（接口即测试缝，不引 Mockito）：reply 即客户端返回。 */
    private record FakeClient(Optional<String> reply) implements LlmChatClient {
        @Override
        public Optional<String> chat(String systemPrompt, String userPrompt, LlmEndpoint endpoint) {
            return reply;
        }
    }

    private static WakePlan plan() {
        return new WakePlan(null, "alarm-1", null, BASELINE, RECOMMENDED, -15,
                TARGET, 0.20, 0.05, 12.0, 0.82,
                List.of(new WakePlan.Factor("TRAVEL_TAIL_RISK", 20, "通勤时长波动余量")),
                "RISK_QUANTILE_MARGIN", null, false, "QUANTILE_V1");
    }

    private static Alarm alarm() {
        Alarm alarm = new Alarm();
        alarm.setName("上班");
        alarm.setDestinationLabel("公司");
        return alarm;
    }

    private static String generate(FakeClient client, int maxChars) {
        return generate(client, maxChars, ENDPOINT);
    }

    private static String generate(FakeClient client, int maxChars, LlmEndpoint endpoint) {
        return new LlmExplanationGenerator(client, maxChars)
                .generate(plan(), alarm(), ZONE, WeatherRisk.unavailable(), endpoint);
    }

    @Test
    void usesLlmTextWhenEndpointActiveAndSucceeds() {
        String text = generate(new FakeClient(
                Optional.of("明早中雨路滑，闹钟提前到 6:55，多留 15 分钟余量更从容。")), 300);
        assertThat(text).isEqualTo("明早中雨路滑，闹钟提前到 6:55，多留 15 分钟余量更从容。");
    }

    @Test
    void fallsBackToTemplateWhenEndpointMissingOrIncomplete() {
        // 端点 null（OFF/已禁用）与半配置端点：都视同 LLM 未启用，不问客户端直接走模板
        // （若误调客户端会拿到 "LLM 文案" 而非模板，断言即失败）
        assertThat(generate(new FakeClient(Optional.empty()), 300, null)).contains("提前到");
        assertThat(generate(new FakeClient(Optional.of("LLM 文案")), 300,
                LlmEndpoint.of("https://llm.example/v1", "", "test-model"))).contains("提前到");
    }

    @Test
    void fallsBackToTemplateWhenClientFailsOrBlank() {
        // 失败（empty）与成功但空白输出，都必须给非空模板文案（explanation 列 NOT NULL）
        assertThat(generate(new FakeClient(Optional.empty()), 300)).contains("提前到");
        assertThat(generate(new FakeClient(Optional.of("   ")), 300)).contains("提前到");
        assertThat(generate(new FakeClient(Optional.of("")), 300)).isNotBlank();
    }

    @Test
    void sanitizesMarkdownAndCollapsesWhitespace() {
        String text = generate(new FakeClient(
                Optional.of("**明早**有雨，`建议`早起。\n第二段不该存在。")), 300);
        assertThat(text).doesNotContain("**").doesNotContain("`").doesNotContain("\n");
        assertThat(text).startsWith("明早有雨");
        assertThat(text).contains("第二段不该存在。"); // 换行折叠为空格，内容不丢
    }

    @Test
    void truncatesToConfiguredMaxChars() {
        String longText = "长".repeat(120);
        assertThat(generate(new FakeClient(Optional.of(longText)), 50)).hasSize(50);
    }

    @Test
    void maxCharsClampedToColumnLimit() {
        // 配置写飞了也不能超 decision_history.explanation 的 VARCHAR(1000)
        String huge = "字".repeat(2000);
        assertThat(generate(new FakeClient(Optional.of(huge)), 5000))
                .hasSize(LlmExplanationGenerator.HARD_COLUMN_LIMIT);
    }
}
