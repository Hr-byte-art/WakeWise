package com.wjh.wakewise.domain.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.wjh.wakewise.domain.alarm.Alarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

/**
 * 高德 Web 服务客户端（地理编码 + 路径规划），镜像 OpenMeteoClient：
 * 紧超时、全异常 Optional.empty() 降级。key 未配置（wakewise.amap.key 留空）
 * 时整体不启用，估算链路回落用户手填通勤分钟。
 *
 * 方式映射：WALK→v3 walking；DRIVE→v3 driving；BIKE→v4 bicycling；
 * TRANSIT/MIXED→v3 transit/integrated（MIXED 取公交复合方案，保守）；NONE 不查询。
 */
@Component
public class AmapClient {

    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);

    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String DIRECTION_V3_URL = "https://restapi.amap.com/v3/direction/";
    private static final String BICYCLING_V4_URL = "https://restapi.amap.com/v4/direction/bicycling";

    private final RestClient restClient;
    private final String key;

    public AmapClient(@Value("${wakewise.amap.key:}") String key) {
        this.key = key == null ? "" : key.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return !key.isEmpty();
    }

    /** 地址文本 → "lng,lat"（首个结果）。 */
    public Optional<String> geocode(String address) {
        if (!configured() || address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(GEOCODE_URL)
                    .queryParam("key", key)
                    .queryParam("address", address.trim())
                    .build()
                    .encode()
                    .toUri();
            return AmapResponseParser.extractGeocode(restClient.get().uri(uri).retrieve().body(JsonNode.class));
        } catch (Exception e) {
            log.warn("Amap geocode failed for {}: {}", address, e.getMessage());
            return Optional.empty();
        }
    }

    /** 起终点（"lng,lat"）按方式的路径耗时（秒）。 */
    public Optional<Integer> routeDurationSeconds(String origin, String destination,
                                                  Alarm.TravelMode mode, String city) {
        if (!configured() || origin == null || destination == null || mode == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = switch (mode) {
                case WALK -> getJson(DIRECTION_V3_URL + "walking", origin, destination, null);
                case DRIVE -> getJson(DIRECTION_V3_URL + "driving", origin, destination, null);
                case TRANSIT, MIXED -> getJson(DIRECTION_V3_URL + "transit/integrated",
                        origin, destination, city);
                case BIKE -> getBicyclingJson(origin, destination);
                case NONE -> null;
            };
            if (root == null) {
                return Optional.empty();
            }
            return switch (mode) {
                case WALK, DRIVE -> AmapResponseParser.extractPathsDuration(root);
                case TRANSIT, MIXED -> AmapResponseParser.extractTransitDuration(root);
                case BIKE -> AmapResponseParser.extractBicyclingDuration(root);
                case NONE -> Optional.empty();
            };
        } catch (Exception e) {
            log.warn("Amap route failed: mode={}, {}", mode, e.getMessage());
            return Optional.empty();
        }
    }

    /** "lng,lat" → [lng, lat]；格式非法返回 null。 */
    public static BigDecimal[] parseLocation(String lngLat) {
        if (lngLat == null) {
            return null;
        }
        String[] parts = lngLat.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new BigDecimal[]{new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode getJson(String url, String origin, String destination, String city) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("key", key)
                .queryParam("origin", origin)
                .queryParam("destination", destination);
        if (city != null && !city.isBlank()) {
            builder.queryParam("city", city.trim());
        }
        URI uri = builder.build().encode().toUri();
        return restClient.get().uri(uri).retrieve().body(JsonNode.class);
    }

    private JsonNode getBicyclingJson(String origin, String destination) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BICYCLING_V4_URL)
                .queryParam("key", key)
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .build()
                .encode()
                .toUri();
        return restClient.get().uri(uri).retrieve().body(JsonNode.class);
    }
}
