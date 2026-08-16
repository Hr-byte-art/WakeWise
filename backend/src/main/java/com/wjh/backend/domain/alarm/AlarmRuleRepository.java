package com.wjh.backend.domain.alarm;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, String> {
    Optional<AlarmRule> findByAlarmId(String alarmId);
}