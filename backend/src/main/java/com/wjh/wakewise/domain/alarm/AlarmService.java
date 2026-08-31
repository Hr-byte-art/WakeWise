package com.wjh.wakewise.domain.alarm;

import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.domain.decision.DecisionService;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmRuleRepository ruleRepository;
    private final UserService userService;
    private final DecisionService decisionService;

    public AlarmService(AlarmRepository alarmRepository,
                        AlarmRuleRepository ruleRepository,
                        UserService userService,
                        DecisionService decisionService) {
        this.alarmRepository = alarmRepository;
        this.ruleRepository = ruleRepository;
        this.userService = userService;
        this.decisionService = decisionService;
    }

    @Transactional
    public AlarmDtos.AlarmView create(String userId, AlarmDtos.CreateAlarmRequest request) {
        UserAccount user = userService.requireUser(userId);
        Alarm alarm = new Alarm();
        alarm.setId(Ids.newId());
        alarm.setUserId(userId);
        alarm.setName(request.name());
        alarm.setTargetType(parseType(request.targetType()));
        alarm.setRecurrence(normalizeRecurrence(request.recurrence()));
        alarm.setDestinationLabel(request.destinationLabel());
        alarm.setTravelMode(parseMode(request.travelMode()));
        if (alarm.getTargetType() == Alarm.AlarmType.WAKE_AT) {
            alarm.setBaselineTime(parseTime(request.baselineTime(), "baselineTime"));
        } else {
            alarm.setTargetLocalTime(parseTime(request.targetLocalTime(), "targetLocalTime"));
        }
        // 手动 ID 的实体 save() 走 merge 路径，所有字段（含 nextTriggerAt）必须在 save 前设置完成
        alarm.setNextTriggerAt(computeNextTrigger(alarm, user));
        alarmRepository.save(alarm);

        AlarmRule rule = new AlarmRule();
        rule.setId(Ids.newId());
        rule.setAlarmId(alarm.getId());
        applyRuleInput(rule, request.rule());
        ruleRepository.save(rule);

        if (alarm.getTargetType() == Alarm.AlarmType.ARRIVE_BY) {
            decisionService.evaluateAndPropose(alarm, user, "ALARM_CREATED");
        }
        return AlarmDtos.AlarmView.from(alarm, rule);
    }

    @Transactional(readOnly = true)
    public List<AlarmDtos.AlarmView> list(String userId) {
        List<Alarm> alarms = alarmRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, AlarmRule> rules = ruleRepository.findByAlarmIdIn(
                        alarms.stream().map(Alarm::getId).toList()).stream()
                .collect(Collectors.toMap(AlarmRule::getAlarmId, Function.identity()));
        return alarms.stream()
                .map(alarm -> AlarmDtos.AlarmView.from(alarm, rules.get(alarm.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlarmDtos.AlarmView get(String userId, String alarmId) {
        Alarm alarm = requireOwned(userId, alarmId);
        AlarmRule rule = ruleRepository.findByAlarmId(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟规则不存在"));
        return AlarmDtos.AlarmView.from(alarm, rule);
    }

    @Transactional
    public AlarmDtos.AlarmView update(String userId, String alarmId, AlarmDtos.UpdateAlarmRequest request) {
        UserAccount user = userService.requireUser(userId);
        Alarm alarm = requireOwned(userId, alarmId);
        if (alarm.getVersion() != request.version()) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT, "闹钟已被修改，请刷新后重试");
        }
        if (request.enabled() != null) {
            alarm.setEnabled(request.enabled());
        }
        if (request.name() != null && !request.name().isBlank()) {
            alarm.setName(request.name());
        }
        AlarmRule rule = ruleRepository.findByAlarmId(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟规则不存在"));
        if (request.rule() != null) {
            applyRuleInput(rule, request.rule());
        }
        alarm.setNextTriggerAt(alarm.isEnabled() ? computeNextTrigger(alarm, user) : null);
        if (alarm.isEnabled() && alarm.getTargetType() == Alarm.AlarmType.ARRIVE_BY) {
            decisionService.evaluateAndPropose(alarm, user, "ALARM_UPDATED");
        }
        return AlarmDtos.AlarmView.from(alarm, rule);
    }

    @Transactional
    public void delete(String userId, String alarmId) {
        Alarm alarm = requireOwned(userId, alarmId);
        alarm.setEnabled(false);
        alarm.setNextTriggerAt(null);
        decisionService.supersedePendingForAlarm(alarmId);
    }

    public Instant computeNextTrigger(Alarm alarm, UserAccount user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Set<DayOfWeek> days = Recurrence.parse(alarm.getRecurrence());
        LocalTime time = alarm.getTargetType() == Alarm.AlarmType.WAKE_AT
                ? alarm.getBaselineTime()
                : alarm.getTargetLocalTime();
        return Recurrence.nextOccurrence(days, time, zone, Instant.now());
    }

    private Alarm requireOwned(String userId, String alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在"));
        if (!alarm.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在");
        }
        return alarm;
    }

    private void applyRuleInput(AlarmRule rule, AlarmDtos.RuleInput input) {
        rule.setPrepDurationMin(value(input.prepDurationMin(), rule.getPrepDurationMin(), 30));
        rule.setCommuteMinutes(value(input.commuteMinutes(), rule.getCommuteMinutes(), 0));
        rule.setMaxAdvanceMin(value(input.maxAdvanceMin(), rule.getMaxAdvanceMin(), 30));
        rule.setMaxDelayMin(value(input.maxDelayMin(), rule.getMaxDelayMin(), 10));
        rule.setAllowAutoDelay(input.allowAutoDelay() != null ? input.allowAutoDelay() : rule.isAllowAutoDelay());
        rule.setAutomationLevel(value(input.automationLevel(), rule.getAutomationLevel(), 1));
        rule.setSnoozeIntervalMin(value(input.snoozeIntervalMin(), rule.getSnoozeIntervalMin(), 5));
        rule.setMaxSnoozeCount(value(input.maxSnoozeCount(), rule.getMaxSnoozeCount(), 2));
        if (rule.getMaxAdvanceMin() > 180 || rule.getMaxDelayMin() > 60) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "调整边界超出限制（提前<=180，延后<=60）");
        }
    }

    private int value(Integer input, int current, int fallback) {
        return input != null ? input : current;
    }

    private Alarm.AlarmType parseType(String type) {
        try {
            return Alarm.AlarmType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetType 必须为 WAKE_AT 或 ARRIVE_BY");
        }
    }

    private Alarm.TravelMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        try {
            return Alarm.TravelMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "travelMode 必须为 WALK/BIKE/TRANSIT/DRIVE/MIXED/NONE");
        }
    }

    private String normalizeRecurrence(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        Set<DayOfWeek> ignored = Recurrence.parse(csv);
        return csv.replace(" ", "").toUpperCase();
    }

    private LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 不能为空");
        }
        try {
            return LocalTime.parse(value.length() == 5 ? value + ":00" : value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 格式应为 HH:mm");
        }
    }
}
