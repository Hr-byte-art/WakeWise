package com.wjh.wakewise.domain.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjh.wakewise.ai.ExplanationGenerator;
import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRepository;
import com.wjh.wakewise.domain.alarm.AlarmRule;
import com.wjh.wakewise.domain.alarm.AlarmRuleRepository;
import com.wjh.wakewise.domain.alarm.Recurrence;
import com.wjh.wakewise.domain.decision.DecisionHistory.PlanStatus;
import com.wjh.wakewise.domain.history.WakeHistory;
import com.wjh.wakewise.domain.history.WakeHistoryRepository;
import com.wjh.wakewise.domain.notification.NotificationService;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import com.wjh.wakewise.domain.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 决策编排：上下文组装 -> 引擎计算 -> 策略判定（自动执行 / 待确认）-> 持久化 + 通知。
 * LLM 不参与时间计算，解释文本只引用计划中的事实。
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);
    private static final double AUTO_ARM_MIN_CONFIDENCE = 0.70;

    private final DecisionHistoryRepository decisionRepository;
    private final AlarmRepository alarmRepository;
    private final AlarmRuleRepository ruleRepository;
    private final WakeHistoryRepository wakeHistoryRepository;
    private final WeatherService weatherService;
    private final DecisionEngine engine;
    private final ExplanationGenerator explanationGenerator;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private final int autoArmMaxAdvance;

    public DecisionService(DecisionHistoryRepository decisionRepository,
                           AlarmRepository alarmRepository,
                           AlarmRuleRepository ruleRepository,
                           WakeHistoryRepository wakeHistoryRepository,
                           WeatherService weatherService,
                           DecisionEngine engine,
                           ExplanationGenerator explanationGenerator,
                           NotificationService notificationService,
                           ObjectMapper objectMapper,
                           @Value("${wakewise.decision.auto-arm-max-advance-minutes:30}") int autoArmMaxAdvance) {
        this.decisionRepository = decisionRepository;
        this.alarmRepository = alarmRepository;
        this.ruleRepository = ruleRepository;
        this.wakeHistoryRepository = wakeHistoryRepository;
        this.weatherService = weatherService;
        this.engine = engine;
        this.explanationGenerator = explanationGenerator;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.autoArmMaxAdvance = autoArmMaxAdvance;
    }

    // ------------------------------------------------------------------ 评估

    @Transactional
    public WakePlan evaluateAndPropose(Alarm alarm, UserAccount user, String trigger) {
        if (alarm.getTargetType() != Alarm.AlarmType.ARRIVE_BY || !alarm.isEnabled()) {
            return null;
        }
        WakePlan computed = compute(alarm, user, null);

        // 同一目标日的旧 PROPOSED 计划作废
        decisionRepository.findByAlarmIdAndTargetAtAndStatusIn(
                        alarm.getId(), computed.targetArrivalAt(), List.of(PlanStatus.PROPOSED))
                .forEach(stale -> stale.setStatus(PlanStatus.SUPERSEDED));

        AlarmRule rule = requireRule(alarm.getId());
        boolean autoArm = rule.getAutomationLevel() >= 2
                && computed.confidence() >= AUTO_ARM_MIN_CONFIDENCE
                && computed.deltaMinutes() <= 0
                && -computed.deltaMinutes() <= autoArmMaxAdvance;

        DecisionHistory history = new DecisionHistory();
        history.setId(Ids.newId());
        history.setPlanId(Ids.newId());
        history.setUserId(user.getId());
        history.setAlarmId(alarm.getId());
        history.setBaselineAt(computed.baselineWakeAt());
        history.setRecommendedAt(computed.recommendedWakeAt());
        history.setTargetAt(computed.targetArrivalAt());
        history.setStatus(autoArm ? PlanStatus.DISPATCHING : PlanStatus.PROPOSED);
        history.setDeltaMinutes(computed.deltaMinutes());
        history.setLateProbabilityBefore(DecisionDtos.bd(computed.lateProbabilityBefore(), 5));
        history.setLateProbabilityAfter(DecisionDtos.bd(computed.lateProbabilityAfter(), 5));
        history.setRiskScore(DecisionDtos.bd(computed.riskScore(), 4));
        history.setConfidence(DecisionDtos.bd(computed.confidence(), 4));
        history.setFactorContributionJson(toJson(computed.topFactors()));
        history.setReasonCode(computed.reasonCode());
        history.setExplanation(computed.explanation());
        history.setModelVersion(computed.modelVersion());
        history.setTriggerType(trigger);
        if (autoArm) {
            history.setEffectiveAt(Instant.now());
        }
        decisionRepository.save(history);

        if (computed.deltaMinutes() != 0) {
            if (autoArm) {
                notificationService.create(user.getId(), history.getPlanId(), "PLAN_AUTO_ADJUSTED",
                        "明早闹钟已自动调整", computed.explanation());
            } else {
                notificationService.create(user.getId(), history.getPlanId(), "PLAN_AWAITING_CONFIRMATION",
                        "晨间计划待确认", computed.explanation());
            }
        }
        log.info("Plan proposed: alarm={}, planId={}, delta={}min, status={}",
                alarm.getId(), history.getPlanId(), computed.deltaMinutes(), history.getStatus());
        return withPersistence(computed, history);
    }

    @Transactional
    public WakePlan previewForUser(String userId, String alarmId, UserAccount user, Integer overrideMaxAdvance) {
        Alarm alarm = ownedAlarm(userId, alarmId);
        if (alarm.getTargetType() != Alarm.AlarmType.ARRIVE_BY) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅目标到达型闹钟支持计划预览");
        }
        return compute(alarm, user, overrideMaxAdvance);
    }

    /** 组装上下文并调用引擎（不落库）。 */
    private WakePlan compute(Alarm alarm, UserAccount user, Integer overrideMaxAdvance) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Set<DayOfWeek> days = Recurrence.parse(alarm.getRecurrence());
        Instant targetAt = Recurrence.nextOccurrence(days, alarm.getTargetLocalTime(), zone, Instant.now());
        LocalDate targetDate = LocalDate.ofInstant(targetAt, zone);

        AlarmRule rule = requireRule(alarm.getId());
        WeatherRisk weather = weatherService.getMorningRisk(user, targetDate, zone);
        WakePlan plan = engine.evaluate(alarm, rule, user, weather, targetAt, overrideMaxAdvance);
        String explanation = explanationGenerator.generate(plan, alarm, zone, weather);
        return new WakePlan(plan.planId(), plan.alarmId(), "PREVIEW",
                plan.baselineWakeAt(), plan.recommendedWakeAt(), plan.deltaMinutes(), plan.targetArrivalAt(),
                plan.lateProbabilityBefore(), plan.lateProbabilityAfter(), plan.riskScore(), plan.confidence(),
                plan.topFactors(), plan.reasonCode(), explanation, false, plan.modelVersion());
    }

    // ------------------------------------------------------------------ 确认 / 撤销

    @Transactional
    public DecisionDtos.PlanView confirm(String userId, String planId) {
        DecisionHistory history = ownedPlan(userId, planId);
        switch (history.getStatus()) {
            case PROPOSED -> {
                history.setStatus(PlanStatus.DISPATCHING);
                history.setEffectiveAt(Instant.now());
            }
            case DISPATCHING, ARMED -> {
                // 幂等：重复确认返回当前状态
            }
            default -> throw new BusinessException(ErrorCode.DECISION_NOT_CONFIRMABLE,
                    "当前状态无法确认: " + history.getStatus());
        }
        return DecisionDtos.PlanView.from(history);
    }

    @Transactional
    public DecisionDtos.PlanView revert(String userId, String planId, String reason) {
        DecisionHistory history = ownedPlan(userId, planId);
        if (history.getStatus() != PlanStatus.ARMED && history.getStatus() != PlanStatus.DISPATCHING
                && history.getStatus() != PlanStatus.PROPOSED) {
            throw new BusinessException(ErrorCode.DECISION_ALREADY_EXECUTED, "计划已执行或已失效，无法撤销");
        }
        history.setStatus(PlanStatus.REVERTED);

        DecisionHistory restore = new DecisionHistory();
        restore.setId(Ids.newId());
        restore.setPlanId(Ids.newId());
        restore.setUserId(history.getUserId());
        restore.setAlarmId(history.getAlarmId());
        restore.setBaselineAt(history.getBaselineAt());
        restore.setRecommendedAt(history.getBaselineAt());
        restore.setTargetAt(history.getTargetAt());
        restore.setStatus(PlanStatus.DISPATCHING);
        restore.setEffectiveAt(Instant.now());
        restore.setDeltaMinutes(0);
        restore.setLateProbabilityBefore(history.getLateProbabilityBefore());
        restore.setLateProbabilityAfter(history.getLateProbabilityBefore());
        restore.setRiskScore(history.getRiskScore());
        restore.setConfidence(history.getConfidence());
        restore.setFactorContributionJson("[]");
        restore.setReasonCode("USER_REVERT");
        restore.setExplanation("已按你的要求恢复原计划。");
        restore.setModelVersion(DecisionEngine.MODEL_VERSION);
        restore.setTriggerType("USER_REVERT");
        decisionRepository.save(restore);

        notificationService.create(userId, restore.getPlanId(), "PLAN_REVERTED",
                "已恢复原计划", restore.getExplanation());
        return DecisionDtos.PlanView.from(restore);
    }

    @Transactional(readOnly = true)
    public DecisionDtos.PlanView getByPlanId(String userId, String planId) {
        return DecisionDtos.PlanView.from(ownedPlan(userId, planId));
    }

    // ------------------------------------------------------------------ 设备同步（ACK 可靠性链路）

    @Transactional(readOnly = true)
    public List<DecisionDtos.PendingPlanView> pending(String userId) {
        List<DecisionHistory> dispatching = decisionRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.DISPATCHING);
        LinkedHashMap<String, DecisionHistory> latestPerAlarm = new LinkedHashMap<>();
        for (DecisionHistory history : dispatching) {
            latestPerAlarm.putIfAbsent(history.getAlarmId(), history);
        }
        return latestPerAlarm.values().stream()
                .map(history -> new DecisionDtos.PendingPlanView(
                        history.getPlanId(), history.getAlarmId(),
                        history.getRecommendedAt().toString(), history.getTargetAt().toString(),
                        history.getDeltaMinutes(), history.getExplanation(), history.getVersion()))
                .toList();
    }

    @Transactional
    public DecisionDtos.PlanView ack(String userId, String planId) {
        DecisionHistory history = ownedPlan(userId, planId);
        if (history.getStatus() == PlanStatus.ARMED) {
            return DecisionDtos.PlanView.from(history); // 幂等：重复 ACK 返回相同结果
        }
        if (history.getStatus() != PlanStatus.DISPATCHING) {
            throw new BusinessException(ErrorCode.DECISION_NOT_CONFIRMABLE, "计划不在待下发状态: " + history.getStatus());
        }
        history.setStatus(PlanStatus.ARMED);
        history.setEffectiveAt(Instant.now());

        // 新计划落地成功后，同一闹钟的其它待下发/已布防计划全部失效
        List<DecisionHistory> others = decisionRepository.findByAlarmIdAndStatusIn(
                history.getAlarmId(), List.of(PlanStatus.ARMED, PlanStatus.DISPATCHING));
        for (DecisionHistory other : others) {
            if (!other.getPlanId().equals(planId)) {
                other.setStatus(PlanStatus.SUPERSEDED);
            }
        }
        alarmRepository.findById(history.getAlarmId())
                .ifPresent(alarm -> alarm.setNextTriggerAt(history.getRecommendedAt()));
        log.info("Plan armed: planId={}, alarm={}", planId, history.getAlarmId());
        return DecisionDtos.PlanView.from(history);
    }

    @Transactional
    public void recordDeviceEvent(String userId, DecisionDtos.DeviceEventRequest request) {
        DecisionHistory history = ownedPlan(userId, request.planId());
        WakeHistory wakeHistory = wakeHistoryRepository.findByPlanId(history.getPlanId())
                .orElseGet(() -> {
                    WakeHistory created = new WakeHistory();
                    created.setId(Ids.newId());
                    created.setUserId(userId);
                    created.setAlarmId(history.getAlarmId());
                    created.setPlanId(history.getPlanId());
                    created.setScheduledAt(history.getRecommendedAt());
                    // save 返回托管实例，保证后续字段修改能被 dirty-check 捕获
                    return wakeHistoryRepository.save(created);
                });
        Instant occurredAt = request.occurredAt() == null || request.occurredAt().isBlank()
                ? Instant.now()
                : Instant.parse(request.occurredAt());
        switch (request.type()) {
            case "ALARM_FIRED" -> wakeHistory.setTriggeredAt(occurredAt);
            case "ALARM_SNOOZED" -> wakeHistory.setSnoozeCount(wakeHistory.getSnoozeCount() + 1);
            case "ALARM_DISMISSED" -> {
                if (wakeHistory.getTriggeredAt() == null) {
                    wakeHistory.setTriggeredAt(occurredAt);
                }
                wakeHistory.setDismissedAt(occurredAt);
                wakeHistory.setOutcome(occurredAt.isAfter(history.getTargetAt()) ? "LATE" : "SUCCESS");
                history.setStatus(PlanStatus.EXECUTED);
            }
            default -> log.debug("Ignoring device event type {}", request.type());
        }
    }

    // ------------------------------------------------------------------ 供夜间任务 / 简报 / 统计

    @Transactional
    public void supersedePendingForAlarm(String alarmId) {
        decisionRepository.findByAlarmIdAndStatusIn(alarmId, List.of(PlanStatus.PROPOSED, PlanStatus.DISPATCHING))
                .forEach(history -> history.setStatus(PlanStatus.SUPERSEDED));
    }

    @Transactional(readOnly = true)
    public DecisionHistory latestPlanFor(String alarmId, Instant targetAt) {
        return decisionRepository.findFirstByAlarmIdAndTargetAtAndStatusInOrderByCreatedAtDesc(
                alarmId, targetAt,
                List.of(PlanStatus.PROPOSED, PlanStatus.DISPATCHING, PlanStatus.ARMED, PlanStatus.EXECUTED))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DecisionHistory> decisionsAfter(String userId, Instant after) {
        return decisionRepository.findByUserIdAndCreatedAtAfter(userId, after);
    }

    // ------------------------------------------------------------------ 内部工具

    private WakePlan withPersistence(WakePlan plan, DecisionHistory history) {
        return new WakePlan(history.getPlanId(), plan.alarmId(), history.getStatus().name(),
                plan.baselineWakeAt(), plan.recommendedWakeAt(), plan.deltaMinutes(), plan.targetArrivalAt(),
                plan.lateProbabilityBefore(), plan.lateProbabilityAfter(), plan.riskScore(), plan.confidence(),
                plan.topFactors(), plan.reasonCode(), history.getExplanation(),
                history.getStatus() == PlanStatus.PROPOSED, plan.modelVersion());
    }

    private Alarm ownedAlarm(String userId, String alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在"));
        if (!alarm.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在");
        }
        return alarm;
    }

    private DecisionHistory ownedPlan(String userId, String planId) {
        DecisionHistory history = decisionRepository.findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "计划不存在"));
        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "计划不存在");
        }
        return history;
    }

    private AlarmRule requireRule(String alarmId) {
        return ruleRepository.findByAlarmId(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟规则不存在"));
    }

    private String toJson(List<WakePlan.Factor> factors) {
        try {
            return objectMapper.writeValueAsString(factors == null ? List.of() : factors);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
