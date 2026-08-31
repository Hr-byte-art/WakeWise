package com.wjh.wakewise.domain.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "weather_cache")
public class WeatherSnapshot {

    @Id
    @Column(length = 26)
    private String id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "forecast_time", nullable = false)
    private Instant forecastTime;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "temperature_c", precision = 5, scale = 2)
    private BigDecimal temperatureC;

    @Column(name = "precip_probability", precision = 5, scale = 4)
    private BigDecimal precipProbability;

    @Column(name = "precip_mm", precision = 8, scale = 2)
    private BigDecimal precipMm;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "alert_level", length = 24)
    private String alertLevel;

    @Column(name = "quality_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
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
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Instant getForecastTime() { return forecastTime; }
    public void setForecastTime(Instant forecastTime) { this.forecastTime = forecastTime; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }
    public BigDecimal getPrecipProbability() { return precipProbability; }
    public void setPrecipProbability(BigDecimal precipProbability) { this.precipProbability = precipProbability; }
    public BigDecimal getPrecipMm() { return precipMm; }
    public void setPrecipMm(BigDecimal precipMm) { this.precipMm = precipMm; }
    public Integer getWeatherCode() { return weatherCode; }
    public void setWeatherCode(Integer weatherCode) { this.weatherCode = weatherCode; }
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
