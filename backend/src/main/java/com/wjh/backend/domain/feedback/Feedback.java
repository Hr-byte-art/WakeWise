package com.wjh.backend.domain.feedback;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "decision_id", length = 26)
    private String decisionId;

    @Column(name = "wake_history_id", length = 26)
    private String wakeHistoryId;

    @Column(nullable = false)
    private int rating;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getWakeHistoryId() { return wakeHistoryId; }
    public void setWakeHistoryId(String wakeHistoryId) { this.wakeHistoryId = wakeHistoryId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
}