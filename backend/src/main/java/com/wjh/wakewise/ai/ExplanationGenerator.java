package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;

import java.time.ZoneId;

/**
 * 解释生成端口。MVP 使用确定性模板（TemplateExplanationGenerator）；
 * 后续接入 Spring AI 时替换实现即可——LLM 只允许引用 WakePlan 中已存在的事实，不允许编造。
 */
public interface ExplanationGenerator {

    String generate(WakePlan plan, Alarm alarm, ZoneId zone, WeatherRisk weather);
}
