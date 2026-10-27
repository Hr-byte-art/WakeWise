package com.wjh.wakewise.domain.behavior;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BehaviorLearningRepository extends JpaRepository<BehaviorLearning, String> {

    Optional<BehaviorLearning> findByUserIdAndSegmentKeyAndFeatureKey(
            String userId, String segmentKey, String featureKey);
}
