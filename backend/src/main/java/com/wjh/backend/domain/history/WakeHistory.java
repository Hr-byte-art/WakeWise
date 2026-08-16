package com.wjh.backend.domain.history;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wake_history")
public class WakeHistory {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "alarm_id", nullable = false, length = 26)
    private String alarmId;

    @Column(name = "plan_id", length = 26)
    private String planId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "woke_at")
    private Instant wokeAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "snooze_count", nullable = false)
    private int snoozeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "source_flags")
    private String sourceFlags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Outcome { SUCCESS, LATE, MISSED, UNKNOWN }

    @PrePersist
    void prePersist() {
        if (id == null) id = UlidCreator.getUlid().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    public Instant getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(Instant dismissedAt) { this.dismissedAt = dismissedAt; }
    public Instant getWokeAt() { return wokeAt; }
    public void setWokeAt(Instant wokeAt) { this.wokeAt = wokeAt; }
    public Instant getDepartedAt() { return departedAt; }
    public void setDepartedAt(Instant departedAt) { this.departedAt = departedAt; }
    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(int snoozeCount) { this.snoozeCount = snoozeCount; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getSourceFlags() { return sourceFlags; }
    public void setSourceFlags(String sourceFlags) { this.sourceFlags = sourceFlags; }
    public Instant getCreatedAt() { return createdAt; }
}