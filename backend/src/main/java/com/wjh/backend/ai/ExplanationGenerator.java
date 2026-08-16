package com.wjh.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjh.backend.domain.decision.WakePlan;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 使用 LangChain4j 根据决策因素生成用户可读的自然语言解释
 * LLM 只负责解释文案，不参与时间计算
 */
@Component
public class ExplanationGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExplanationGenerator.class);

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;

    public ExplanationGenerator(ChatLanguageModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 为 WakePlan 生成解释文案
     */
    public String generate(WakePlan plan) {
        if (plan.topFactors() == null || plan.topFactors().isEmpty()) {
            return "天气状况良好，按原计划时间起床。";
        }

        String factorsText = plan.topFactors().stream()
                .map(f -> f.code() + ": +" + f.impactMinutes() + "分钟")
                .collect(Collectors.joining("，"));

        String prompt = String.format("""
                你是一个智能闹钟助手的解释模块。请用一句简短、自然的中文解释以下闹钟调整。
                
                调整幅度：%d 分钟（负值表示提前）
                主要影响因素：%s
                置信度：%.0f%%
                
                要求：
                - 一句话，不超过 50 字
                - 说明主要原因和调整幅度
                - 语气温和、不制造焦虑
                - 不要编造数据，只使用给定的因素
                
                示例："预计明早降雨，通勤时间可能增加，建议提前 15 分钟起床。"
                """,
                plan.deltaMinutes(), factorsText, plan.confidence() * 100);

        try {
            String explanation = chatModel.generate(prompt).trim();
            log.debug("LLM 生成解释: {}", explanation);
            return explanation;
        } catch (Exception e) {
            log.warn("LLM 解释生成失败，使用规则兜底: {}", e.getMessage());
            return fallbackExplanation(plan);
        }
    }

    /**
     * LLM 不可用时的规则兜底解释
     */
    private String fallbackExplanation(WakePlan plan) {
        if (plan.deltaMinutes() >= 0) {
            return "天气状况良好，按原计划时间起床。";
        }
        int advance = Math.abs(plan.deltaMinutes());
        if (plan.topFactors().isEmpty()) {
            return String.format("建议提前 %d 分钟起床。", advance);
        }
        String mainFactor = plan.topFactors().get(0).code();
        String factorName = switch (mainFactor) {
            case "PRECIP" -> "降雨";
            case "VISIBILITY" -> "低能见度";
            case "TEMP" -> "极端温度";
            case "WIND" -> "大风";
            case "AQI" -> "空气质量";
            default -> "天气变化";
        };
        return String.format("预计明早%s，建议提前 %d 分钟起床。", factorName, advance);
    }
}