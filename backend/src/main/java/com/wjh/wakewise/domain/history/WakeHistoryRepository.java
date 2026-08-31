package com.wjh.wakewise.domain.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WakeHistoryRepository extends JpaRepository<WakeHistory, String> {

    Optional<WakeHistory> findByPlanId(String planId);

    List<WakeHistory> findByUserIdAndScheduledAtAfter(String userId, Instant after);
}
