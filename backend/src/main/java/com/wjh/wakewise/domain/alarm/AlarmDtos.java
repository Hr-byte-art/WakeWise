package com.wjh.wakewise.domain.alarm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AlarmDtos {

    private AlarmDtos() {
    }

    public record RuleInput(
            @Min(0) Integer prepDurationMin,
            @Min(0) Integer commuteMinutes,
            @Min(0) Integer maxAdvanceMin,
            @Min(0) Integer maxDelayMin,
            Boolean allowAutoDelay,
            Integer automationLevel,
            Integer snoozeIntervalMin,
            Integer maxSnoozeCount) {

    };

    public record CreateAlarmRequest(
            @NotBlank String name,
            @NotBlank String targetType,
            String baselineTime,
            String targetLocalTime,
            String recurrence,
            String destinationLabel,
            String travelMode,
            @NotNull RuleInput rule) {
    }

    public record UpdateAlarmRequest(
            @NotNull Long version,
            Boolean enabled,
            String name,
            RuleInput rule) {
    }

    public record PreviewRequest(Integer maxAdvanceMinutes) {
    }

    public record RuleView(
            int prepDurationMin,
            int commuteMinutes,
            int maxAdvanceMin,
            int maxDelayMin,
            boolean allowAutoDelay,
            int automationLevel,
            int snoozeIntervalMin,
            int maxSnoozeCount) {

        public static RuleView from(AlarmRule rule) {
            return new RuleView(rule.getPrepDurationMin(), rule.getCommuteMinutes(),
                    rule.getMaxAdvanceMin(), rule.getMaxDelayMin(), rule.isAllowAutoDelay(),
                    rule.getAutomationLevel(), rule.getSnoozeIntervalMin(), rule.getMaxSnoozeCount());
        }
    }

    public record AlarmView(
            String id,
            String name,
            String targetType,
            boolean enabled,
            String baselineTime,
            String targetLocalTime,
            String recurrence,
            String destinationLabel,
            String travelMode,
            String nextTriggerAt,
            long version,
            RuleView rule) {

        public static AlarmView from(Alarm alarm, AlarmRule rule) {
            return new AlarmView(
                    alarm.getId(), alarm.getName(), alarm.getTargetType().name(), alarm.isEnabled(),
                    alarm.getBaselineTime() == null ? null : alarm.getBaselineTime().toString(),
                    alarm.getTargetLocalTime() == null ? null : alarm.getTargetLocalTime().toString(),
                    alarm.getRecurrence(), alarm.getDestinationLabel(),
                    alarm.getTravelMode() == null ? null : alarm.getTravelMode().name(),
                    alarm.getNextTriggerAt() == null ? null : alarm.getNextTriggerAt().toString(),
                    alarm.getVersion(), RuleView.from(rule));
        }
    }
}
