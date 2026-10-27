package com.wjh.wakewise.domain.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TrafficSnapshotRepository extends JpaRepository<TrafficSnapshot, String> {

    Optional<TrafficSnapshot> findFirstByRouteKeyAndExpiresAtAfterOrderByObservedAtDesc(
            String routeKey, Instant now);
}
