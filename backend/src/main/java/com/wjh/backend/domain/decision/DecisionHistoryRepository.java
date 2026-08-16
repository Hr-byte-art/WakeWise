package com.wjh.backend.domain.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, String> {
    Optional<DecisionHistory> findByPlanId(String planId);
    List<DecisionHistory> findByAlarmIdAndStatusOrderByCreatedAtDesc(String alarmId, DecisionHistory.DecisionStatus status);
    List<DecisionHistory> findByUserIdAndStatusInOrderByCreatedAtDesc(String userId, List<DecisionHistory.DecisionStatus> statuses);
}