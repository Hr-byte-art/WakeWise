package com.wjh.backend.domain.user;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "device_id", nullable = false, unique = true, length = 128)
    private String deviceId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_preference", nullable = false)
    private RiskPreference riskPreference;

    @Column(name = "automation_level", nullable = false)
    private int automationLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public enum RiskPreference { BALANCED, CONSERVATIVE, SLEEP_FIRST }
    public enum AccountStatus { ACTIVE, LOCKED, DELETING, DELETED }

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

    // --- factory ---
    public static UserAccount create(String deviceId, String displayName) {
        UserAccount u = new UserAccount();
        u.deviceId = deviceId;
        u.displayName = displayName != null ? displayName : "";
        u.timezone = "Asia/Shanghai";
        u.locale = "zh-CN";
        u.riskPreference = RiskPreference.BALANCED;
        u.automationLevel = 0;
        u.status = AccountStatus.ACTIVE;
        return u;
    }

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public RiskPreference getRiskPreference() { return riskPreference; }
    public void setRiskPreference(RiskPreference riskPreference) { this.riskPreference = riskPreference; }
    public int getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(int automationLevel) { this.automationLevel = automationLevel; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}