package com.wjh.wakewise.ai;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.decision.WakePlan;
import com.wjh.wakewise.domain.weather.WeatherRisk;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * LLM 解释的提示词组装（纯函数、确定性）。
 *
 * 红线在提示词侧就锁死：user prompt 只序列化 WakePlan/Alarm/WeatherRisk 中
 * 已存在的事实（时间均为引擎算好后的本地时刻），system prompt 明令禁止编造、
 * 推算或更改任何时间与数字——LLM 只做文案改写，不参与时间计算。
 */
public final class LlmPromptBuilder {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private LlmPromptBuilder() {
    }

    /** 系统提示词：角色、铁律与语气基准。maxChars 与生成器侧硬截断一致。 */
    public static String systemPrompt(int maxChars) {
        return "你是 AI 晨间助手\"知晨\"的文案生成器。唯一任务：把给定的唤醒计划事实"
                + "改写成一句自然、温和、简短的中文解释。"
                + "铁律：1）只能使用输入事实中给出的时间、数字与措辞，禁止编造、推算或更改任何时间与数字；"
                + "2）你不参与任何时间计算，计算已由确定性引擎完成；"
                + "3）输出一段话，不超过 " + maxChars + " 字，不使用 Markdown、列表或换行；"
                + "4）语气直接、具体、安抚但不夸张。"
                + "示例（仅语气参考，内容以输入事实为准）："
                + "明早闹钟建议从 7:10 提前到 6:55，为 8:45 前到达「公司」预留更充裕的时间。";
    }

    /** 用户提示词：结构化事实清单，全部来自计划与上下文，无任何推导值。 */
    public static String facts(WakePlan plan, Alarm alarm, ZoneId zone, WeatherRisk weather) {
        String baseline = HHMM.format(plan.baselineWakeAt().atZone(zone));
        String recommended = HHMM.format(plan.recommendedWakeAt().atZone(zone));
        String target = HHMM.format(plan.targetArrivalAt().atZone(zone));
        String destination = alarm.getDestinationLabel() == null || alarm.getDestinationLabel().isBlank()
                ? "目的地" : alarm.getDestinationLabel();

        StringBuilder sb = new StringBuilder("【唤醒计划事实】\n");
        if (plan.deltaMinutes() == 0) {
            sb.append("结论: 保持 ").append(baseline).append(" 不变，无需调整\n");
        } else if (plan.deltaMinutes() < 0) {
            sb.append("结论: 从 ").append(baseline).append(" 提前到 ").append(recommended)
                    .append("（提前 ").append(-plan.deltaMinutes()).append(" 分钟）\n");
        } else {
            sb.append("结论: 从 ").append(baseline).append(" 延后到 ").append(recommended)
                    .append("（延后 ").append(plan.deltaMinutes()).append(" 分钟）\n");
        }
        sb.append("目标到达: ").append(target).append(" · 目的地「").append(destination).append("」");
        if (alarm.getTravelMode() != null) {
            sb.append(" · 出行方式 ").append(alarm.getTravelMode());
        }
        sb.append('\n');
        sb.append("天气: ").append(weatherSummary(weather)).append('\n');
        List<WakePlan.Factor> factors = plan.topFactors() == null ? List.of() : plan.topFactors();
        if (!factors.isEmpty()) {
            StringBuilder joined = new StringBuilder();
            for (WakePlan.Factor f : factors) {
                if (joined.length() > 0) {
                    joined.append("、");
                }
                joined.append(f.label()).append("（约 +").append(f.impactMinutes()).append(" 分钟）");
            }
            sb.append("主要因素: ").append(joined).append('\n');
        }
        sb.append("模型置信度: ").append(Math.round(plan.confidence() * 100)).append("%（")
                .append(plan.modelVersion()).append("）");
        return sb.toString();
    }

    private static String weatherSummary(WeatherRisk weather) {
        if (weather == null || !weather.available()) {
            return "暂无天气数据";
        }
        String level = weather.heavy() ? "大雨" : weather.moderate() ? "中雨" : weather.light() ? "小雨" : "无降水";
        return level + "，" + String.format("%.0f°C", weather.temperatureC());
    }
}
