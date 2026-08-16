package com.wjh.backend.domain.alarm;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "alarm_rule")
public class AlarmRule {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "alarm_id", nullable = false, unique = true, length = 26)
    private String alarmId;

    @Column(length = 512)
    private String rrule;

    @Column(name = "prep_duration_min", nullable = false)
    private int prepDurationMin;

    @Column(name = "max_advance_min", nullable = false)
    private int maxAdvanceMin;

    @Column(name = "max_delay_min", nullable = false)
    private int maxDelayMin;

    @Column(name = "allow_auto_delay", nullable = false)
    private boolean allowAutoDelay;

    @Column(name = "automation_level", nullable = false)
    private int automationLevel;

    @Column(name = "snooze_interval_min", nullable = false)
    private int snoozeIntervalMin;

    @Column(name = "max_snooze_count", nullable = false)
    private int maxSnoozeCount;

    @Column(name = "wake_strategy_json", columnDefinition = "JSON", nullable = false)
    private String wakeStrategyJson;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UlidCreator.getUlid().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static AlarmRule createDefault(String alarmId) {
        AlarmRule r = new AlarmRule();
        r.alarmId = alarmId;
        r.prepDurationMin = 30;
        r.maxAdvanceMin = 30;
        r.maxDelayMin = 10;
        r.allowAutoDelay = false;
        r.automationLevel = 0;
        r.snoozeIntervalMin = 5;
        r.maxSnoozeCount = 2;
        r.wakeStrategyJson = "{\"sound\":\"default\",\"vibrate\":true,\"gradual\":true}";
        return r;
    }

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public String getRrule() { return rrule; }
    public void setRrule(String rrule) { this.rrule = rrule; }
    public int getPrepDurationMin() { return prepDurationMin; }
    public void setPrepDurationMin(int prepDurationMin) { this.prepDurationMin = prepDurationMin; }
    public int getMaxAdvanceMin() { return maxAdvanceMin; }
    public void setMaxAdvanceMin(int maxAdvanceMin) { this.maxAdvanceMin = maxAdvanceMin; }
    public int getMaxDelayMin() { return maxDelayMin; }
    public void setMaxDelayMin(int maxDelayMin) { this.maxDelayMin = maxDelayMin; }
    public boolean isAllowAutoDelay() { return allowAutoDelay; }
    public void setAllowAutoDelay(boolean allowAutoDelay) { this.allowAutoDelay = allowAutoDelay; }
    public int getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(int automationLevel) { this.automationLevel = automationLevel; }
    public int getSnoozeIntervalMin() { return snoozeIntervalMin; }
    public void setSnoozeIntervalMin(int snoozeIntervalMin) { this.snoozeIntervalMin = snoozeIntervalMin; }
    public int getMaxSnoozeCount() { return maxSnoozeCount; }
    public void setMaxSnoozeCount(int maxSnoozeCount) { this.maxSnoozeCount = maxSnoozeCount; }
    public String getWakeStrategyJson() { return wakeStrategyJson; }
    public void setWakeStrategyJson(String wakeStrategyJson) { this.wakeStrategyJson = wakeStrategyJson; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(LocalDate effectiveUntil) { this.effectiveUntil = effectiveUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}