package com.wjh.backend.domain.decision;

import com.wjh.backend.domain.alarm.Alarm;
import com.wjh.backend.domain.alarm.AlarmRule;
import com.wjh.backend.domain.alarm.AlarmService;
import com.wjh.backend.domain.notification.NotificationService;
import com.wjh.backend.domain.weather.WeatherCache;
import com.wjh.backend.domain.weather.WeatherService;
import com.wjh.backend.infrastructure.BusinessException;
import com.wjh.backend.infrastructure.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionEngine decisionEngine;
    private final DecisionHistoryRepository decisionRepo;
    private final AlarmService alarmService;
    private final WeatherService weatherService;
    private final NotificationService notificationService;

    public DecisionService(DecisionEngine decisionEngine,
                           DecisionHistoryRepository decisionRepo,
                           AlarmService alarmService,
                           WeatherService weatherService,
                           NotificationService notificationService) {
        this.decisionEngine = decisionEngine;
        this.decisionRepo = decisionRepo;
        this.alarmService = alarmService;
        this.weatherService = weatherService;
        this.notificationService = notificationService;
    }

    /**
     * 对指定闹钟执行评估，生成新计划
     */
    @Transactional
    public WakePlan evaluate(String alarmId, Instant targetDate, String geoHash) {
        Alarm alarm = alarmService.getById(alarmId);
        AlarmRule rule = alarmService.getRule(alarmId);

        // 获取天气数据
        WeatherCache weather = null;
        if (geoHash != null && !geoHash.isBlank()) {
            weather = weatherService.getWeather(geoHash, targetDate);
        }

        // 执行决策
        WakePlan plan = decisionEngine.evaluate(alarm, rule, weather, targetDate);

        // 持久化
        DecisionHistory entity = decisionEngine.toEntity(plan);
        decisionRepo.save(entity);

        // 发送通知
        if (plan.requiresConfirmation()) {
            notificationService.sendWithDecision(
                    alarm.getUserId(), plan.planId(),
                    "PLAN_PENDING",
                    "闹钟计划需要确认",
                    String.format("明早闹钟建议调整为 %s，提前 %d 分钟",
                            plan.recommendedWakeAt(), Math.abs(plan.deltaMinutes()))
            );
        } else {
            notificationService.sendWithDecision(
                    alarm.getUserId(), plan.planId(),
                    "PLAN_ADJUSTED",
                    "闹钟已自动调整",
                    String.format("明早闹钟已从 %s 调整为 %s",
                            plan.baselineWakeAt(), plan.recommendedWakeAt())
            );
        }

        return plan;
    }

    /**
     * 查询计划
     */
    public DecisionHistory getPlan(String planId) {
        return decisionRepo.findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "计划不存在"));
    }

    /**
     * 用户确认计划
     */
    @Transactional
    public DecisionHistory confirm(String planId, long version) {
        DecisionHistory dh = getPlan(planId);
        if (dh.getVersion() != version) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT, "计划已被修改");
        }
        if (dh.getStatus() != DecisionHistory.DecisionStatus.PENDING
                && dh.getStatus() != DecisionHistory.DecisionStatus.PROPOSED) {
            throw new BusinessException(ErrorCode.DECISION_EXPIRED, "计划已过期或已执行");
        }
        dh.setStatus(DecisionHistory.DecisionStatus.ARMED);
        dh.setEffectiveAt(Instant.now());
        return decisionRepo.save(dh);
    }

    /**
     * 用户撤销计划，恢复原时间
     */
    @Transactional
    public DecisionHistory revert(String planId, long version) {
        DecisionHistory dh = getPlan(planId);
        if (dh.getVersion() != version) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT, "计划已被修改");
        }
        if (dh.getStatus() == DecisionHistory.DecisionStatus.EXECUTED) {
            throw new BusinessException(ErrorCode.DECISION_ALREADY_EXECUTED, "计划已执行，无法撤销");
        }
        dh.setStatus(DecisionHistory.DecisionStatus.REVERTED);
        return decisionRepo.save(dh);
    }

    /**
     * 获取用户待处理的计划
     */
    public List<DecisionHistory> getPendingPlans(String userId) {
        return decisionRepo.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                List.of(DecisionHistory.DecisionStatus.PROPOSED, DecisionHistory.DecisionStatus.PENDING));
    }
}