package com.wjh.backend.domain.alarm;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "alarm")
public class Alarm {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "baseline_time")
    private LocalTime baselineTime;

    @Column(name = "target_at")
    private Instant targetAt;

    @Column(name = "calendar_event_ref", length = 160)
    private String calendarEventRef;

    @Column(name = "destination_label", length = 200)
    private String destinationLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode")
    private TravelMode travelMode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "next_trigger_at")
    private Instant nextTriggerAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public enum TargetType { WAKE_AT, LEAVE_BY, ARRIVE_BY, EVENT }
    public enum TravelMode { WALK, BIKE, TRANSIT, DRIVE, MIXED, NONE }

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

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TargetType getTargetType() { return targetType; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    public LocalTime getBaselineTime() { return baselineTime; }
    public void setBaselineTime(LocalTime baselineTime) { this.baselineTime = baselineTime; }
    public Instant getTargetAt() { return targetAt; }
    public void setTargetAt(Instant targetAt) { this.targetAt = targetAt; }
    public String getCalendarEventRef() { return calendarEventRef; }
    public void setCalendarEventRef(String calendarEventRef) { this.calendarEventRef = calendarEventRef; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public TravelMode getTravelMode() { return travelMode; }
    public void setTravelMode(TravelMode travelMode) { this.travelMode = travelMode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getNextTriggerAt() { return nextTriggerAt; }
    public void setNextTriggerAt(Instant nextTriggerAt) { this.nextTriggerAt = nextTriggerAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}