package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;

import java.time.ZoneId;

/**
 * 解释生成端口。当前实现：LlmExplanationGenerator（OpenAI 兼容端点做文案改写，
 * 未配置/失败回落确定性模板）——LLM 只允许引用 WakePlan 中已存在的事实，不允许编造。
 */
public interface ExplanationGenerator {

    /**
     * llmEndpoint 为解析后的生效端点（用户级 BYOK ＞ 服务端默认，见 CredentialService）；
     * null/不完整表示 LLM 未启用，LLM 实现回落确定性模板（模板实现忽略该参数）。
     */
    String generate(WakePlan plan, Alarm alarm, ZoneId zone, WeatherRisk weather, LlmEndpoint llmEndpoint);
}
