package com.wjh.wakewise.domain.alarm;

import com.wjh.wakewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alarm_rule")
public class AlarmRule extends BaseEntity {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "alarm_id", nullable = false, unique = true, length = 26)
    private String alarmId;

    @Column(name = "prep_duration_min", nullable = false)
    private int prepDurationMin = 30;

    @Column(name = "commute_minutes", nullable = false)
    private int commuteMinutes = 0;

    @Column(name = "max_advance_min", nullable = false)
    private int maxAdvanceMin = 30;

    @Column(name = "max_delay_min", nullable = false)
    private int maxDelayMin = 10;

    @Column(name = "allow_auto_delay", nullable = false)
    private boolean allowAutoDelay = false;

    @Column(name = "automation_level", nullable = false)
    private int automationLevel = 1;

    @Column(name = "snooze_interval_min", nullable = false)
    private int snoozeIntervalMin = 5;

    @Column(name = "max_snooze_count", nullable = false)
    private int maxSnoozeCount = 2;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public int getPrepDurationMin() { return prepDurationMin; }
    public void setPrepDurationMin(int prepDurationMin) { this.prepDurationMin = prepDurationMin; }
    public int getCommuteMinutes() { return commuteMinutes; }
    public void setCommuteMinutes(int commuteMinutes) { this.commuteMinutes = commuteMinutes; }
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
}
