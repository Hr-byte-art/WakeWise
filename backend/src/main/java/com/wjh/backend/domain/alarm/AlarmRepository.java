package com.wjh.backend.domain.alarm;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, String> {
    List<Alarm> findByUserIdAndEnabledTrue(String userId);
    List<Alarm> findByUserId(String userId);
}