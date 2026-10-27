package com.wjh.wakewise.domain.morning;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRepository;
import com.wjh.wakewise.domain.alarm.AlarmRule;
import com.wjh.wakewise.domain.alarm.AlarmRuleRepository;
import com.wjh.wakewise.domain.alarm.Recurrence;
import com.wjh.wakewise.domain.behavior.BehaviorLearningService;
import com.wjh.wakewise.domain.behavior.WakeLatencyEstimator;
import com.wjh.wakewise.domain.decision.DecisionEngine;
import com.wjh.wakewise.domain.decision.DecisionHistory;
import com.wjh.wakewise.domain.decision.DecisionService;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.user.UserService;
import com.wjh.wakewise.domain.weather.WeatherRisk;
import com.wjh.wakewise.domain.weather.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** 晨间简报：下一次唤醒、建议出门时间、天气与首个事件。 */
@Service
public class MorningContextService {

    private final AlarmRepository alarmRepository;
    private final AlarmRuleRepository ruleRepository;
    private final UserService userService;
    private final WeatherService weatherService;
    private final DecisionEngine engine;
    private final DecisionService decisionService;
    private final BehaviorLearningService behaviorLearningService;

    public MorningContextService(AlarmRepository alarmRepository,
                                 AlarmRuleRepository ruleRepository,
                                 UserService userService,
                                 WeatherService weatherService,
                                 DecisionEngine engine,
                                 DecisionService decisionService,
                                 BehaviorLearningService behaviorLearningService) {
        this.alarmRepository = alarmRepository;
        this.ruleRepository = ruleRepository;
        this.userService = userService;
        this.weatherService = weatherService;
        this.engine = engine;
        this.decisionService = decisionService;
        this.behaviorLearningService = behaviorLearningService;
    }

    public record WakePlanView(String wakeAt, String leaveBy, String arriveBy,
                               String planId, Integer deltaMinutes, String explanation,
                               Boolean requiresConfirmation) {
    }

    public record WeatherView(String summary, String level, String alert, List<String> suggestions) {
    }

    public record TrafficView(String mode, int etaP50Minutes, int etaP90Minutes, String status) {
    }

    public record EventView(String title, String startAt, String locationLabel) {
    }

    /** 校准信息：null 表示尚未校准（样本不足或无记录）。 */
    public record CalibrationView(Integer sampleCount, Double estimateMinutes, String algorithmVersion) {
    }

    public record MorningContextView(String date, WakePlanView wakePlan, WeatherView weather,
                                     TrafficView traffic, EventView firstEvent, String updatedAt,
                                     CalibrationView calibration) {
    }

    @Transactional(readOnly = true)
    public MorningContextView forUser(String userId, LocalDate requestedDate) {
        UserAccount user = userService.requireUser(userId);
        ZoneId zone = ZoneId.of(user.getTimezone());
        DecisionEngine.BehavioralPriors priors = behaviorLearningService.priorsFor(userId);
        LocalDate target = requestedDate != null
                ? requestedDate
                : LocalDate.ofInstant(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS), zone);

        Instant bestWake = null;
        Instant bestLeave = null;
        Instant bestArrive = null;
        String bestPlanId = null;
        Integer bestDelta = null;
        String bestExplanation = null;
        Boolean bestRequiresConfirmation = null;
        String eventTitle = null;
        String eventLocation = null;
        Instant eventStart = null;
        String travelMode = null;
        int etaP50 = 0;
        int etaP90 = 0;

        for (Alarm alarm : alarmRepository.findByUserIdAndEnabledTrue(userId)) {
            Set<DayOfWeek> days = Recurrence.parse(alarm.getRecurrence());
            if (!days.contains(target.getDayOfWeek())) {
                continue;
            }
            AlarmRule rule = ruleRepository.findByAlarmId(alarm.getId()).orElse(null);
            if (rule == null) {
                continue;
            }
            if (alarm.getTargetType() == Alarm.AlarmType.ARRIVE_BY) {
                Instant targetAt = target.atTime(alarm.getTargetLocalTime()).atZone(zone).toInstant();
                DecisionHistory plan = decisionService.latestPlanFor(alarm.getId(), targetAt);
                Instant wake = plan != null ? plan.getRecommendedAt() : engine.baselineWakeAt(rule, targetAt, priors);
                if (bestWake == null || wake.isBefore(bestWake)) {
                    bestWake = wake;
                    bestLeave = engine.leaveBy(rule, wake, priors);
                    bestArrive = targetAt;
                    bestPlanId = plan == null ? null : plan.getPlanId();
                    bestDelta = plan == null ? null : plan.getDeltaMinutes();
                    bestExplanation = plan == null ? null : plan.getExplanation();
                    bestRequiresConfirmation = plan != null && plan.getStatus() == DecisionHistory.PlanStatus.PROPOSED;
                    eventTitle = alarm.getName();
                    eventLocation = alarm.getDestinationLabel();
                    eventStart = targetAt;
                    travelMode = alarm.getTravelMode() == null ? null : alarm.getTravelMode().name();
                    etaP50 = rule.getCommuteMinutes();
                    etaP90 = etaP50 == 0 ? 0 : Math.max(etaP50 + 5, (int) Math.round(etaP50 * 1.3));
                }
            } else {
                Instant wake = target.atTime(alarm.getBaselineTime()).atZone(zone).toInstant();
                if (bestWake == null || wake.isBefore(bestWake)) {
                    bestWake = wake;
                    bestLeave = null;
                    bestArrive = null;
                    bestPlanId = null;
                    bestDelta = null;
                    bestExplanation = null;
                    bestRequiresConfirmation = null;
                    eventTitle = alarm.getName();
                    eventLocation = null;
                    eventStart = wake;
                    travelMode = null;
                    etaP50 = 0;
                    etaP90 = 0;
                }
            }
        }

        WeatherRisk weather = weatherService.getMorningRisk(user, target, zone);
        String levelSummary = switch (weather.level()) {
            case WeatherRisk.LEVEL_HEAVY -> "大雨";
            case WeatherRisk.LEVEL_MODERATE -> "中雨";
            case WeatherRisk.LEVEL_LIGHT -> "小雨";
            default -> "无降水";
        };
        String summary = weather.available()
                ? levelSummary + "，" + String.format("%.0f°C", weather.temperatureC())
                : "暂无天气数据";
        List<String> suggestions = weather.heavy() || weather.moderate() || weather.light()
                ? List.of("携带雨具")
                : List.of();

        return new MorningContextView(
                target.toString(),
                bestWake == null ? null : new WakePlanView(
                        bestWake.toString(),
                        bestLeave == null ? null : bestLeave.toString(),
                        bestArrive == null ? null : bestArrive.toString(),
                        bestPlanId, bestDelta, bestExplanation, bestRequiresConfirmation),
                new WeatherView(summary, weather.level(), weather.alertLevel(), suggestions),
                new TrafficView(travelMode, etaP50, etaP90,
                        etaP50 == 0 ? null : (weather.heavy() || weather.moderate() ? "SLOWER_THAN_USUAL" : "NORMAL")),
                eventTitle == null ? null : new EventView(eventTitle,
                        eventStart == null ? null : eventStart.toString(), eventLocation),
                Instant.now().toString(),
                priors.sampleCount() > 0 ? new CalibrationView(priors.sampleCount(),
                        priors.wakeLatencyP50Min(), WakeLatencyEstimator.ALGORITHM_VERSION) : null);
    }
}
