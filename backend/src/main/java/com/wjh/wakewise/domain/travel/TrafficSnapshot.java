package com.wjh.wakewise.domain.travel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** 路径规划耗时快照（V4，镜像 weather_cache 模式：2 小时 TTL）。 */
@Entity
@Table(name = "traffic_cache")
public class TrafficSnapshot {

    @Id
    @Column(length = 26)
    private String id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "route_key", nullable = false, length = 64)
    private String routeKey;

    @Column(name = "travel_mode", nullable = false, length = 16)
    private String travelMode;

    @Column(name = "eta_p50_sec", nullable = false)
    private int etaP50Sec;

    @Column(name = "eta_p90_sec")
    private Integer etaP90Sec;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "quality_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String routeKey) { this.routeKey = routeKey; }
    public String getTravelMode() { return travelMode; }
    public void setTravelMode(String travelMode) { this.travelMode = travelMode; }
    public int getEtaP50Sec() { return etaP50Sec; }
    public void setEtaP50Sec(int etaP50Sec) { this.etaP50Sec = etaP50Sec; }
    public Integer getEtaP90Sec() { return etaP90Sec; }
    public void setEtaP90Sec(Integer etaP90Sec) { this.etaP90Sec = etaP90Sec; }
    public Integer getDistanceM() { return distanceM; }
    public void setDistanceM(Integer distanceM) { this.distanceM = distanceM; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
