package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;

/**
 * LLM 解释生成（增强链路）：把引擎算好的计划事实交给 OpenAI 兼容端点做文案改写。
 * 任何失败（未配置/超时/报错/空白输出）即时回落确定性模板，绝不抛错——
 * "AI 可以失败，闹钟不能"。红线：LLM 只写文案不算时间（见 LlmPromptBuilder）。
 */
@Component
public class LlmExplanationGenerator implements ExplanationGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmExplanationGenerator.class);

    /** explanation 落库列 VARCHAR(1000)（decision_history 与 notification.body 同限），上限收紧防越界。 */
    static final int HARD_COLUMN_LIMIT = 1000;

    private final LlmChatClient client;
    private final TemplateExplanationGenerator fallback = new TemplateExplanationGenerator();
    private final int maxChars;

    public LlmExplanationGenerator(LlmChatClient client,
                                   @Value("${wakewise.llm.max-chars:300}") int maxChars) {
        this.client = client;
        this.maxChars = Math.min(Math.max(maxChars, 50), HARD_COLUMN_LIMIT);
    }

    @Override
    public String generate(WakePlan plan, Alarm alarm, ZoneId zone, WeatherRisk weather, LlmEndpoint llmEndpoint) {
        if (llmEndpoint == null || !llmEndpoint.complete()) {
            return fallback.generate(plan, alarm, zone, weather, llmEndpoint);
        }
        String system = LlmPromptBuilder.systemPrompt(maxChars);
        String user = LlmPromptBuilder.facts(plan, alarm, zone, weather);
        Optional<String> generated = client.chat(system, user, llmEndpoint)
                .map(this::sanitize)
                .filter(s -> !s.isBlank());
        if (generated.isPresent()) {
            log.debug("LLM explanation generated: plan={}", plan.planId());
            return generated.get();
        }
        log.info("LLM explanation unavailable, falling back to template: plan={}", plan.planId());
        return fallback.generate(plan, alarm, zone, weather, llmEndpoint);
    }

    /** 净化：折叠空白、去常见 Markdown 记号，硬截断到落库列宽以内。 */
    private String sanitize(String raw) {
        String s = raw.replaceAll("\\s+", " ")
                .replace("**", "")
                .replace("`", "")
                .replace("#", "")
                .trim();
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }
}
