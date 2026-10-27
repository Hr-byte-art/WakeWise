package com.wjh.wakewise.domain.alarm;

import com.wjh.wakewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "alarm")
public class Alarm extends BaseEntity {

    public enum AlarmType { WAKE_AT, ARRIVE_BY }

    public enum TravelMode { WALK, BIKE, TRANSIT, DRIVE, MIXED, NONE }

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private AlarmType targetType;

    @Column(name = "baseline_time")
    private LocalTime baselineTime;

    @Column(name = "target_local_time")
    private LocalTime targetLocalTime;

    @Column(length = 64)
    private String recurrence;

    @Column(name = "destination_label", length = 120)
    private String destinationLabel;

    /** 目的地地理编码坐标缓存（V4，destination_label 仍是文本主输入）。 */
    @Column(name = "dest_lng", precision = 10, scale = 6)
    private BigDecimal destLng;

    @Column(name = "dest_lat", precision = 9, scale = 6)
    private BigDecimal destLat;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", length = 16)
    private TravelMode travelMode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "next_trigger_at")
    private Instant nextTriggerAt;

    @Version
    @Column(nullable = false)
    private long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AlarmType getTargetType() { return targetType; }
    public void setTargetType(AlarmType targetType) { this.targetType = targetType; }
    public LocalTime getBaselineTime() { return baselineTime; }
    public void setBaselineTime(LocalTime baselineTime) { this.baselineTime = baselineTime; }
    public LocalTime getTargetLocalTime() { return targetLocalTime; }
    public void setTargetLocalTime(LocalTime targetLocalTime) { this.targetLocalTime = targetLocalTime; }
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public BigDecimal getDestLng() { return destLng; }
    public void setDestLng(BigDecimal destLng) { this.destLng = destLng; }
    public BigDecimal getDestLat() { return destLat; }
    public void setDestLat(BigDecimal destLat) { this.destLat = destLat; }
    public TravelMode getTravelMode() { return travelMode; }
    public void setTravelMode(TravelMode travelMode) { this.travelMode = travelMode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getNextTriggerAt() { return nextTriggerAt; }
    public void setNextTriggerAt(Instant nextTriggerAt) { this.nextTriggerAt = nextTriggerAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
