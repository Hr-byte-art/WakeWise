package com.wjh.backend.domain.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    List<Feedback> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Feedback> findByDecisionId(String decisionId);
}