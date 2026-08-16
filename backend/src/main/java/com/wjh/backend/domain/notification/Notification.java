package com.wjh.backend.domain.notification;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "decision_id", length = 26)
    private String decisionId;

    @Column(nullable = false, length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 160)
    private String providerMessageId;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Channel { IN_APP, PUSH, EMAIL, WEARABLE }
    public enum NotificationStatus { PENDING, SENT, DELIVERED, OPENED, FAILED }

    @PrePersist
    void prePersist() {
        if (id == null) id = UlidCreator.getUlid().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static Notification create(String userId, String type, String title, String body) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.channel = Channel.IN_APP;
        n.title = title;
        n.body = body;
        n.status = NotificationStatus.PENDING;
        n.idempotencyKey = UlidCreator.getUlid().toString();
        return n;
    }

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getCreatedAt() { return createdAt; }
}