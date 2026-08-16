package com.wjh.backend.domain.weather;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "weather_cache")
public class WeatherCache {

    @Id
    @Column(length = 26)
    private String id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "geo_hash", nullable = false, length = 12)
    private String geoHash;

    @Column(name = "forecast_time", nullable = false)
    private Instant forecastTime;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "temperature_c", precision = 5, scale = 2)
    private BigDecimal temperatureC;

    @Column(name = "feels_like_c", precision = 5, scale = 2)
    private BigDecimal feelsLikeC;

    @Column(name = "precip_probability", precision = 5, scale = 4)
    private BigDecimal precipProbability;

    @Column(name = "precip_mm", precision = 8, scale = 2)
    private BigDecimal precipMm;

    @Column(name = "visibility_m")
    private Integer visibilityM;

    @Column(name = "wind_speed_mps", precision = 6, scale = 2)
    private BigDecimal windSpeedMps;

    @Column
    private Integer aqi;

    @Column(name = "alert_level", length = 24)
    private String alertLevel;

    @Column(name = "quality_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "payload_json", columnDefinition = "JSON", nullable = false)
    private String payloadJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getGeoHash() { return geoHash; }
    public void setGeoHash(String geoHash) { this.geoHash = geoHash; }
    public Instant getForecastTime() { return forecastTime; }
    public void setForecastTime(Instant forecastTime) { this.forecastTime = forecastTime; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }
    public BigDecimal getFeelsLikeC() { return feelsLikeC; }
    public void setFeelsLikeC(BigDecimal feelsLikeC) { this.feelsLikeC = feelsLikeC; }
    public BigDecimal getPrecipProbability() { return precipProbability; }
    public void setPrecipProbability(BigDecimal precipProbability) { this.precipProbability = precipProbability; }
    public BigDecimal getPrecipMm() { return precipMm; }
    public void setPrecipMm(BigDecimal precipMm) { this.precipMm = precipMm; }
    public Integer getVisibilityM() { return visibilityM; }
    public void setVisibilityM(Integer visibilityM) { this.visibilityM = visibilityM; }
    public BigDecimal getWindSpeedMps() { return windSpeedMps; }
    public void setWindSpeedMps(BigDecimal windSpeedMps) { this.windSpeedMps = windSpeedMps; }
    public Integer getAqi() { return aqi; }
    public void setAqi(Integer aqi) { this.aqi = aqi; }
    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}