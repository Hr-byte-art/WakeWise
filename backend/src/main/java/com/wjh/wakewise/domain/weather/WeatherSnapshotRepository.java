package com.wjh.wakewise.domain.weather;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface WeatherSnapshotRepository extends JpaRepository<WeatherSnapshot, String> {

    Optional<WeatherSnapshot> findFirstByCityAndForecastTimeAndExpiresAtAfterOrderByObservedAtDesc(
            String city, Instant forecastTime, Instant now);
}
