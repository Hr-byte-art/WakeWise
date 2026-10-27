package com.wjh.wakewise.domain.history;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, String> {

    boolean existsByEventId(String eventId);
}
