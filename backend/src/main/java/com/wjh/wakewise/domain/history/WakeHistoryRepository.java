package com.wjh.wakewise.domain.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WakeHistoryRepository extends JpaRepository<WakeHistory, String> {

    Optional<WakeHistory> findByPlanId(String planId);

    List<WakeHistory> findByUserIdAndScheduledAtAfter(String userId, Instant after);

    /** 最近的有效起床样本（已响铃且已关闭），行为学习校准用；固定取 50 条，max-samples 需 <= 50。 */
    List<WakeHistory> findTop50ByUserIdAndTriggeredAtIsNotNullAndDismissedAtIsNotNullOrderByScheduledAtDesc(
            String userId);
}
