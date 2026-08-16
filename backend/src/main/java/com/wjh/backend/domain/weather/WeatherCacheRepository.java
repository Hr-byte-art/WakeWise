package com.wjh.backend.domain.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface WeatherCacheRepository extends JpaRepository<WeatherCache, String> {
    Optional<WeatherCache> findFirstByProviderAndGeoHashAndForecastTimeOrderByObservedAtDesc(
            String provider, String geoHash, Instant forecastTime);
}