package com.wjh.wakewise.domain.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, String> {

    List<Alarm> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Alarm> findByUserIdAndEnabledTrue(String userId);

    List<Alarm> findByEnabledTrue();
}
