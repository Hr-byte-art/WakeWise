package com.wjh.backend.domain.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface WakeHistoryRepository extends JpaRepository<WakeHistory, String> {
    List<WakeHistory> findByUserIdAndScheduledAtBetweenOrderByScheduledAtDesc(
            String userId, Instant start, Instant end);

    @Query("SELECT COUNT(w) FROM WakeHistory w WHERE w.userId = :userId " +
           "AND w.scheduledAt BETWEEN :start AND :end AND w.outcome = 'SUCCESS'")
    long countSuccessByUserAndPeriod(@Param("userId") String userId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    @Query("SELECT COUNT(w) FROM WakeHistory w WHERE w.userId = :userId " +
           "AND w.scheduledAt BETWEEN :start AND :end")
    long countTotalByUserAndPeriod(@Param("userId") String userId,
                                    @Param("start") Instant start,
                                    @Param("end") Instant end);
}