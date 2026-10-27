package com.wjh.wakewise.domain.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    /** 出门时刻（USER_LEFT_HOME 事件写入，行为学习一期只积累不消费）。 */
    @Column(name = "left_home_at")
    private Instant leftHomeAt;

    @Column(name = "snooze_count", nullable = false)
    private int snoozeCount = 0;

    @Column(nullable = false, length = 16)
    private String outcome = "UNKNOWN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * 应用一条设备事件（纯状态迁移，字段级幂等，供 DecisionService 事件入口调用）。
     *
     * ALARM_FIRED 只记第一次响铃：贪睡后同 planId 会再次触发并重报 FIRED，
     * 若按 last-write 覆盖，校准样本 latency = dismissedAt − 末次响铃，会把贪睡时间
     * 整段挤掉，与估计算法"贪睡计入起床延迟"的建模语义相反（见 WakeLatencyEstimator 注释）。
     */
    public void applyDeviceEvent(String type, Instant occurredAt, Instant targetAt) {
        switch (type) {
            case "ALARM_FIRED" -> {
                if (triggeredAt == null) {
                    triggeredAt = occurredAt;
                }
            }
            case "ALARM_SNOOZED" -> snoozeCount++;
            case "ALARM_DISMISSED" -> {
                if (triggeredAt == null) {
                    triggeredAt = occurredAt;
                }
                dismissedAt = occurredAt;
                outcome = occurredAt.isAfter(targetAt) ? "LATE" : "SUCCESS";
            }
            case "USER_LEFT_HOME" -> {
                // 字段级幂等：outbox 重放/重复上报只记第一次出门时刻
                if (leftHomeAt == null) {
                    leftHomeAt = occurredAt;
                }
            }
            default -> { /* 未知事件由调用方记日志，这里不动状态 */ }
        }
    }

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
    public Instant getLeftHomeAt() { return leftHomeAt; }
    public void setLeftHomeAt(Instant leftHomeAt) { this.leftHomeAt = leftHomeAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(int snoozeCount) { this.snoozeCount = snoozeCount; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
