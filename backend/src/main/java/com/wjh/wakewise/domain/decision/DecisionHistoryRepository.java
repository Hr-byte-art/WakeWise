package com.wjh.wakewise.domain.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, String> {

    Optional<DecisionHistory> findByPlanId(String planId);

    List<DecisionHistory> findByUserIdOrderByCreatedAtDesc(String userId);

    List<DecisionHistory> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, DecisionHistory.PlanStatus status);

    List<DecisionHistory> findByAlarmIdAndStatusIn(String alarmId, Collection<DecisionHistory.PlanStatus> statuses);

    List<DecisionHistory> findByAlarmIdAndTargetAtAndStatusIn(String alarmId, Instant targetAt,
                                                              Collection<DecisionHistory.PlanStatus> statuses);

    Optional<DecisionHistory> findFirstByAlarmIdAndTargetAtAndStatusInOrderByCreatedAtDesc(
            String alarmId, Instant targetAt, Collection<DecisionHistory.PlanStatus> statuses);

    List<DecisionHistory> findByUserIdAndCreatedAtAfter(String userId, Instant after);
}
