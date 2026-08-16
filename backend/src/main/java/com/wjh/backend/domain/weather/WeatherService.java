package com.wjh.backend.domain.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherCacheRepository cacheRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wakewise.weather.provider}")
    private String provider;

    @Value("${wakewise.weather.api-key}")
    private String apiKey;

    @Value("${wakewise.weather.base-url}")
    private String baseUrl;

    @Value("${wakewise.weather.cache-ttl-minutes}")
    private int cacheTtlMinutes;

    public WeatherService(WeatherCacheRepository cacheRepo, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.cacheRepo = cacheRepo;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取指定位置和时间的天气数据，优先从缓存读取
     */
    public WeatherCache getWeather(String geoHash, Instant forecastTime) {
        // 先查缓存
        return cacheRepo.findFirstByProviderAndGeoHashAndForecastTimeOrderByObservedAtDesc(
                        provider, geoHash, forecastTime)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseGet(() -> fetchAndCache(geoHash, forecastTime));
    }

    private WeatherCache fetchAndCache(String geoHash, Instant forecastTime) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("未配置天气 API Key，返回空缓存");
            return createEmptyCache(geoHash, forecastTime);
        }

        try {
            // 和风天气 24小时逐时预报 API
            String url = baseUrl + "/weather/24h?location=" + geoHash + "&key=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (!"200".equals(root.path("code").asText())) {
                log.warn("天气 API 返回错误: {}", response);
                return createEmptyCache(geoHash, forecastTime);
            }

            JsonNode hourly = root.path("hourly");
            JsonNode target = findClosestHour(hourly, forecastTime);

            WeatherCache cache = new WeatherCache();
            cache.setProvider(provider);
            cache.setGeoHash(geoHash);
            cache.setForecastTime(forecastTime);
            cache.setObservedAt(Instant.now());
            cache.setQualityScore(BigDecimal.valueOf(0.9));

            if (target != null) {
                cache.setTemperatureC(safeDecimal(target.path("temp")));
                cache.setFeelsLikeC(safeDecimal(target.path("feelsLike")));
                cache.setPrecipProbability(safeDecimal(target.path("pop")).divide(BigDecimal.valueOf(100)));
                cache.setPrecipMm(safeDecimal(target.path("precip")));
                cache.setVisibilityM(safeInt(target.path("vis")));
                cache.setWindSpeedMps(safeDecimal(target.path("windSpeed")));
            }

            cache.setPayloadJson(response);
            cache.setExpiresAt(Instant.now().plus(cacheTtlMinutes, ChronoUnit.MINUTES));
            return cacheRepo.save(cache);

        } catch (Exception e) {
            log.error("获取天气数据失败", e);
            return createEmptyCache(geoHash, forecastTime);
        }
    }

    private JsonNode findClosestHour(JsonNode hourly, Instant target) {
        if (hourly == null || !hourly.isArray()) return null;
        long targetEpoch = target.getEpochSecond();
        JsonNode closest = null;
        long minDiff = Long.MAX_VALUE;
        for (JsonNode h : hourly) {
            long fxTime = Instant.parse(h.path("fxTime").asText()).getEpochSecond();
            long diff = Math.abs(fxTime - targetEpoch);
            if (diff < minDiff) {
                minDiff = diff;
                closest = h;
            }
        }
        return closest;
    }

    private WeatherCache createEmptyCache(String geoHash, Instant forecastTime) {
        WeatherCache cache = new WeatherCache();
        cache.setProvider(provider);
        cache.setGeoHash(geoHash);
        cache.setForecastTime(forecastTime);
        cache.setObservedAt(Instant.now());
        cache.setQualityScore(BigDecimal.ZERO);
        cache.setPayloadJson("{}");
        cache.setExpiresAt(Instant.now().plus(cacheTtlMinutes, ChronoUnit.MINUTES));
        return cacheRepo.save(cache);
    }

    private BigDecimal safeDecimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try { return BigDecimal.valueOf(node.asDouble()); } catch (Exception e) { return null; }
    }

    private Integer safeInt(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try { return node.asInt(); } catch (Exception e) { return null; }
    }
}