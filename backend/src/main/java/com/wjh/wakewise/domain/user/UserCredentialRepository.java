package com.wjh.wakewise.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCredentialRepository extends JpaRepository<UserCredential, String> {

    /** 每用户每服务至多一行（uk_user_credential）。 */
    Optional<UserCredential> findByUserIdAndService(String userId, UserCredential.Service service);
}
