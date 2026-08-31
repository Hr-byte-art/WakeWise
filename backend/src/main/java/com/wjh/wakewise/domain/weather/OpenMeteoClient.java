package com.wjh.wakewise.domain.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Open-Meteo 免费天气与地理编码客户端（无需 API Key）。所有失败返回 Optional.empty()，由上层降级。 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient restClient;

    public OpenMeteoClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record GeoLocation(double latitude, double longitude, String name) {
    }

    public record HourlyForecast(List<String> time,
                                 List<Double> temperatureC,
                                 List<Integer> precipProbability,
                                 List<Double> precipMm,
                                 List<Integer> weatherCode) {
    }

    public Optional<GeoLocation> geocode(String city) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://geocoding-api.open-meteo.com/v1/search")
                    .queryParam("name", city)
                    .queryParam("count", 1)
                    .queryParam("language", "zh")
                    .queryParam("format", "json")
                    .build()
                    .encode()
                    .toUri();
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !root.hasNonNull("results") || root.path("results").isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = root.path("results").get(0);
            return Optional.of(new GeoLocation(
                    first.path("latitude").asDouble(),
                    first.path("longitude").asDouble(),
                    first.path("name").asText(city)));
        } catch (Exception e) {
            log.warn("Open-Meteo geocode failed for {}: {}", city, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<HourlyForecast> forecast(double latitude, double longitude, LocalDate date) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("hourly", "temperature_2m,precipitation_probability,precipitation,weather_code")
                    .queryParam("timezone", "auto")
                    .queryParam("start_date", date.toString())
                    .queryParam("end_date", date.toString())
                    .build()
                    .toUri();
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !root.has("hourly")) {
                return Optional.empty();
            }
            JsonNode hourly = root.path("hourly");
            return Optional.of(new HourlyForecast(
                    readStrings(hourly.path("time")),
                    readDoubles(hourly.path("temperature_2m")),
                    readInts(hourly.path("precipitation_probability")),
                    readDoubles(hourly.path("precipitation")),
                    readInts(hourly.path("weather_code"))));
        } catch (Exception e) {
            log.warn("Open-Meteo forecast failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> readStrings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private List<Double> readDoubles(JsonNode array) {
        List<Double> values = new ArrayList<>();
        array.forEach(node -> values.add(node.isNull() ? 0.0 : node.asDouble()));
        return values;
    }

    private List<Integer> readInts(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(node -> values.add(node.isNull() ? 0 : node.asInt()));
        return values;
    }
}
