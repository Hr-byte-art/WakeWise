package com.wjh.wakewise.domain.user;

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

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    public enum RiskPreference { BALANCED, CONSERVATIVE, SLEEP_FIRST }

    public enum UserStatus { ACTIVE, LOCKED, DELETING, DELETED }

    @Id
    @Column(length = 26)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Shanghai";

    @Column(nullable = false, length = 16)
    private String locale = "zh-CN";

    @Column(length = 80)
    private String city;

    /** 常驻地址（家）文本与地理编码坐标缓存（V4，真实 ETA 的起点）。 */
    @Column(name = "home_address", length = 160)
    private String homeAddress;

    @Column(name = "home_lng", precision = 10, scale = 6)
    private BigDecimal homeLng;

    @Column(name = "home_lat", precision = 9, scale = 6)
    private BigDecimal homeLat;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_preference", nullable = false, length = 32)
    private RiskPreference riskPreference = RiskPreference.BALANCED;

    @Column(name = "automation_level", nullable = false)
    private int automationLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getHomeAddress() { return homeAddress; }
    public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }
    public BigDecimal getHomeLng() { return homeLng; }
    public void setHomeLng(BigDecimal homeLng) { this.homeLng = homeLng; }
    public BigDecimal getHomeLat() { return homeLat; }
    public void setHomeLat(BigDecimal homeLat) { this.homeLat = homeLat; }
    public RiskPreference getRiskPreference() { return riskPreference; }
    public void setRiskPreference(RiskPreference riskPreference) { this.riskPreference = riskPreference; }
    public int getAutomationLevel() { return automationLevel; }
    public void setAutomationLevel(int automationLevel) { this.automationLevel = automationLevel; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
