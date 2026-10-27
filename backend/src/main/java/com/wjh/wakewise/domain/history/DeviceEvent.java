package com.wjh.wakewise.domain.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 设备事件流水（V3）：event_id 全局唯一，用于上报幂等去重——
 * outbox 重放/客户端重试在 DecisionService 入口按存在性跳过，
 * 并发窗口内的重复由 uk_device_event 唯一约束回滚兜底。
 */
@Entity
@Table(name = "device_event")
public class DeviceEvent {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "plan_id", nullable = false, length = 26)
    private String planId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
