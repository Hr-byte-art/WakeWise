package com.wjh.wakewise.domain.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, String> {

    Optional<AlarmRule> findByAlarmId(String alarmId);

    List<AlarmRule> findByAlarmIdIn(Collection<String> alarmIds);
}
