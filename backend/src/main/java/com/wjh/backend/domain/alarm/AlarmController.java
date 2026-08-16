package com.wjh.backend.domain.alarm;

import com.wjh.backend.infrastructure.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping
    public ApiResponse<AlarmResponse> create(@Valid @RequestBody CreateAlarmRequest req) {
        Alarm alarm = new Alarm();
        alarm.setUserId(req.userId());
        alarm.setName(req.name());
        alarm.setTargetType(req.targetType());
        alarm.setBaselineTime(req.baselineTime());
        alarm.setTargetAt(req.targetAt());
        alarm.setDestinationLabel(req.destinationLabel());
        alarm.setTravelMode(req.travelMode());
        alarm.setEnabled(true);

        AlarmRule rule = AlarmRule.createDefault(null);
        if (req.prepDurationMin() != null) rule.setPrepDurationMin(req.prepDurationMin());
        if (req.maxAdvanceMin() != null) rule.setMaxAdvanceMin(req.maxAdvanceMin());
        if (req.maxDelayMin() != null) rule.setMaxDelayMin(req.maxDelayMin());
        if (req.allowAutoDelay() != null) rule.setAllowAutoDelay(req.allowAutoDelay());
        if (req.automationLevel() != null) rule.setAutomationLevel(req.automationLevel());
        if (req.rrule() != null) rule.setRrule(req.rrule());

        Alarm saved = alarmService.create(alarm, rule);
        return ApiResponse.ok(toResponse(saved));
    }

    @GetMapping
    public ApiResponse<List<AlarmResponse>> list(@RequestParam String userId) {
        List<Alarm> alarms = alarmService.listByUser(userId);
        return ApiResponse.ok(alarms.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{alarmId}")
    public ApiResponse<AlarmDetailResponse> get(@PathVariable String alarmId) {
        Alarm alarm = alarmService.getById(alarmId);
        AlarmRule rule = alarmService.getRule(alarmId);
        return ApiResponse.ok(toDetailResponse(alarm, rule));
    }

    @PatchMapping("/{alarmId}")
    public ApiResponse<AlarmResponse> update(@PathVariable String alarmId,
                                             @Valid @RequestBody UpdateAlarmRequest req) {
        Alarm patch = new Alarm();
        patch.setName(req.name());
        patch.setTargetType(req.targetType());
        patch.setBaselineTime(req.baselineTime());
        patch.setTargetAt(req.targetAt());
        patch.setDestinationLabel(req.destinationLabel());
        patch.setTravelMode(req.travelMode());

        Alarm updated = alarmService.update(alarmId, req.version(), patch);

        if (req.hasRuleUpdate()) {
            AlarmRule rulePatch = new AlarmRule();
            if (req.prepDurationMin() != null) rulePatch.setPrepDurationMin(req.prepDurationMin());
            if (req.maxAdvanceMin() != null) rulePatch.setMaxAdvanceMin(req.maxAdvanceMin());
            if (req.maxDelayMin() != null) rulePatch.setMaxDelayMin(req.maxDelayMin());
            if (req.allowAutoDelay() != null) rulePatch.setAllowAutoDelay(req.allowAutoDelay());
            if (req.automationLevel() != null) rulePatch.setAutomationLevel(req.automationLevel());
            if (req.rrule() != null) rulePatch.setRrule(req.rrule());
            alarmService.updateRule(alarmId, rulePatch);
        }

        return ApiResponse.ok(toResponse(updated));
    }

    @DeleteMapping("/{alarmId}")
    public ApiResponse<Void> delete(@PathVariable String alarmId) {
        alarmService.delete(alarmId);
        return ApiResponse.ok(null);
    }

    private AlarmResponse toResponse(Alarm a) {
        return new AlarmResponse(a.getId(), a.getUserId(), a.getName(), a.getTargetType(),
                a.getBaselineTime(), a.getTargetAt(), a.getDestinationLabel(), a.getTravelMode(),
                a.isEnabled(), a.getNextTriggerAt(), a.getVersion());
    }

    private AlarmDetailResponse toDetailResponse(Alarm a, AlarmRule r) {
        return new AlarmDetailResponse(
                a.getId(), a.getUserId(), a.getName(), a.getTargetType(),
                a.getBaselineTime(), a.getTargetAt(), a.getDestinationLabel(), a.getTravelMode(),
                a.isEnabled(), a.getNextTriggerAt(), a.getVersion(),
                r.getPrepDurationMin(), r.getMaxAdvanceMin(), r.getMaxDelayMin(),
                r.isAllowAutoDelay(), r.getAutomationLevel(), r.getRrule(),
                r.getSnoozeIntervalMin(), r.getMaxSnoozeCount(), r.getWakeStrategyJson()
        );
    }

    // --- DTOs ---
    public record CreateAlarmRequest(
            @NotBlank String userId,
            @NotBlank String name,
            @NotNull Alarm.TargetType targetType,
            LocalTime baselineTime,
            Instant targetAt,
            String destinationLabel,
            Alarm.TravelMode travelMode,
            Integer prepDurationMin,
            Integer maxAdvanceMin,
            Integer maxDelayMin,
            Boolean allowAutoDelay,
            Integer automationLevel,
            String rrule
    ) {}

    public record UpdateAlarmRequest(
            @NotNull long version,
            String name,
            Alarm.TargetType targetType,
            LocalTime baselineTime,
            Instant targetAt,
            String destinationLabel,
            Alarm.TravelMode travelMode,
            Integer prepDurationMin,
            Integer maxAdvanceMin,
            Integer maxDelayMin,
            Boolean allowAutoDelay,
            Integer automationLevel,
            String rrule
    ) {
        public boolean hasRuleUpdate() {
            return prepDurationMin != null || maxAdvanceMin != null || maxDelayMin != null
                    || allowAutoDelay != null || automationLevel != null || rrule != null;
        }
    }

    public record AlarmResponse(
            String id, String userId, String name, Alarm.TargetType targetType,
            LocalTime baselineTime, Instant targetAt, String destinationLabel,
            Alarm.TravelMode travelMode, boolean enabled, Instant nextTriggerAt, long version
    ) {}

    public record AlarmDetailResponse(
            String id, String userId, String name, Alarm.TargetType targetType,
            LocalTime baselineTime, Instant targetAt, String destinationLabel,
            Alarm.TravelMode travelMode, boolean enabled, Instant nextTriggerAt, long version,
            int prepDurationMin, int maxAdvanceMin, int maxDelayMin,
            boolean allowAutoDelay, int automationLevel, String rrule,
            int snoozeIntervalMin, int maxSnoozeCount, String wakeStrategyJson
    ) {}
}