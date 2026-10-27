package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 确定性模板解释：只引用计划中的事实（时间、因素、置信度），不引入外部内容。
 * 同时是 LLM 链路的兜底实现——LlmExplanationGenerator 未配置/失败时回落到这里
 * （非 Spring Bean，由调用方直接构造，保证兜底零依赖零故障面）。
 */
public class TemplateExplanationGenerator implements ExplanationGenerator {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String generate(WakePlan plan, Alarm alarm, ZoneId zone, WeatherRisk weather) {
        String baseline = plan.baselineWakeAt().atZone(zone).format(HHMM);
        String recommended = plan.recommendedWakeAt().atZone(zone).format(HHMM);
        String target = plan.targetArrivalAt().atZone(zone).format(HHMM);
        String destination = alarm.getDestinationLabel() == null || alarm.getDestinationLabel().isBlank()
                ? "目的地"
                : alarm.getDestinationLabel();

        StringBuilder sb = new StringBuilder();
        if (plan.deltaMinutes() == 0) {
            sb.append("明早保持 ").append(baseline).append(" 不变，当前条件下无需调整。");
        } else if (plan.deltaMinutes() < 0) {
            sb.append("明早闹钟建议从 ").append(baseline).append(" 提前到 ").append(recommended)
                    .append("，为 ").append(target).append(" 前到达「").append(destination)
                    .append("」预留更充裕的时间。");
        } else {
            sb.append("明早闹钟可以延后 ").append(plan.deltaMinutes()).append(" 分钟（")
                    .append(baseline).append(" → ").append(recommended)
                    .append("），在风险可控的前提下多保留一些睡眠。");
        }

        List<WakePlan.Factor> factors = plan.topFactors() == null ? List.of() : plan.topFactors();
        if (!factors.isEmpty() && plan.deltaMinutes() != 0) {
            String joined = factors.stream()
                    .map(f -> f.label() + "（约 +" + f.impactMinutes() + " 分钟）")
                    .collect(Collectors.joining("、"));
            sb.append("主要因素：").append(joined).append("。");
        }
        if (weather != null && weather.available() && weather.heavy()) {
            sb.append("预报有较强降水，请预留雨天的额外通勤时间。");
        }
        sb.append("当前置信度 ").append(Math.round(plan.confidence() * 100)).append("%。");
        if (plan.deltaMinutes() != 0) {
            sb.append("可一键恢复原计划。");
        }
        return sb.toString();
    }
}
