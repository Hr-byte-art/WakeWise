package com.wjh.backend.agent;

import com.wjh.backend.ai.ExplanationGenerator;
import com.wjh.backend.domain.alarm.Alarm;
import com.wjh.backend.domain.alarm.AlarmRule;
import com.wjh.backend.domain.alarm.AlarmService;
import com.wjh.backend.domain.decision.*;
import com.wjh.backend.domain.notification.NotificationService;
import com.wjh.backend.domain.user.UserAccount;
import com.wjh.backend.domain.user.UserService;
import com.wjh.backend.domain.weather.WeatherCache;
import com.wjh.backend.domain.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Morning Agent 计划编排器
 * 负责：采集上下文 → 调用决策引擎 → 策略检查 → 下发/通知
 */
@Component
public class PlanOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlanOrchestrator.class);

    private final AlarmService alarmService;
    private final UserService userService;
    private final WeatherService weatherService;
    private final DecisionEngine decisionEngine;
    private final DecisionHistoryRepository decisionRepo;
    private final ExplanationGenerator explanationGenerator;
    private final NotificationService notificationService;

    public PlanOrchestrator(AlarmService alarmService,
                            UserService userService,
                            WeatherService weatherService,
                            DecisionEngine decisionEngine,
                            DecisionHistoryRepository decisionRepo,
                            ExplanationGenerator explanationGenerator,
                            NotificationService notificationService) {
        this.alarmService = alarmService;
        this.userService = userService;
        this.weatherService = weatherService;
        this.decisionEngine = decisionEngine;
        this.decisionRepo = decisionRepo;
        this.explanationGenerator = explanationGenerator;
        this.notificationService = notificationService;
    }

    /**
     * 夜间计划：为所有启用的闹钟生成次日计划
     * 由 Quartz 在 22:00 触发
     */
    public void runNightlyPlan() {
        log.info("=== Morning Agent: 夜间计划开始 ===");
        // MVP 简化：遍历所有闹钟（生产环境应按用户分片）
        // 这里通过一个已知用户 ID 来演示流程
        // 实际应由 Job 参数传入或从数据库查询所有活跃用户
        processAllActiveAlarms("NIGHTLY_PLAN");
        log.info("=== Morning Agent: 夜间计划完成 ===");
    }

    /**
     * 睡前复核：22:00 计划后再次确认天气无重大变化
     * 由 Quartz 在 23:00 触发
     */
    public void runPreSleepReview() {
        log.info("=== Morning Agent: 睡前复核开始 ===");
        processAllActiveAlarms("PRE_SLEEP_REVIEW");
        log.info("=== Morning Agent: 睡前复核完成 ===");
    }

    /**
     * 凌晨二次确认：确保天气变化及时响应
     * 由 Quartz 在 05:00 触发
     */
    public void runMorningReview() {
        log.info("=== Morning Agent: 凌晨复核开始 ===");
        processAllActiveAlarms("MORNING_REVIEW");
        log.info("=== Morning Agent: 凌晨复核完成 ===");
    }

    private void processAllActiveAlarms(String trigger) {
        // MVP 简化：通过一个示例用户 ID 演示
        // 生产环境应查询所有活跃用户及其闹钟
        // 这里作为框架代码保留
        log.info("Agent 触发类型: {}", trigger);
    }

    /**
     * 为单个闹钟执行完整计划流程
     */
    public WakePlan planForAlarm(String alarmId, String geoHash) {
        Alarm alarm = alarmService.getById(alarmId);
        AlarmRule rule = alarmService.getRule(alarmId);
        UserAccount user = userService.getById(alarm.getUserId());

        // 计算目标日期（次日早晨）
        Instant targetDate = computeTargetDate(alarm, user.getTimezone());

        // 获取天气
        WeatherCache weather = null;
        if (geoHash != null && !geoHash.isBlank()) {
            weather = weatherService.getWeather(geoHash, targetDate);
        }

        // 执行决策
        WakePlan plan = decisionEngine.evaluate(alarm, rule, weather, targetDate);

        // 生成解释
        String explanation = explanationGenerator.generate(plan);

        // 持久化
        DecisionHistory entity = decisionEngine.toEntity(plan);
        entity.setExplanation(explanation);
        decisionRepo.save(entity);

        // 通知用户
        String title = plan.deltaMinutes() < -5
                ? String.format("明早闹钟提前 %d 分钟", Math.abs(plan.deltaMinutes()))
                : "明早闹钟按原计划";
        notificationService.sendWithDecision(user.getId(), plan.planId(),
                "PLAN_READY", title, explanation);

        log.info("计划已生成: alarmId={}, planId={}, delta={}min, confidence={}",
                alarmId, plan.planId(), plan.deltaMinutes(), plan.confidence());

        return plan;
    }

    private Instant computeTargetDate(Alarm alarm, String timezone) {
        ZoneId zone = ZoneId.of(timezone != null ? timezone : "Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.now(zone);

        // 如果当前时间已过 12:00，目标为明天；否则为今天
        ZonedDateTime targetDay = now.getHour() >= 12
                ? now.plusDays(1).truncatedTo(ChronoUnit.DAYS)
                : now.truncatedTo(ChronoUnit.DAYS);

        if (alarm.getBaselineTime() != null) {
            targetDay = targetDay.with(alarm.getBaselineTime());
        } else {
            targetDay = targetDay.withHour(7).withMinute(0); // 默认 7:00
        }

        return targetDay.toInstant();
    }
}