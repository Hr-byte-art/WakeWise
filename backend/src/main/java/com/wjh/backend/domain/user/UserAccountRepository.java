package com.wjh.backend.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByDeviceId(String deviceId);
}