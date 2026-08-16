package com.wjh.backend.domain.user;

import com.wjh.backend.infrastructure.BusinessException;
import com.wjh.backend.infrastructure.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserAccountRepository userRepo;

    public UserService(UserAccountRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional
    public UserAccount registerOrLogin(String deviceId, String displayName) {
        return userRepo.findByDeviceId(deviceId)
                .map(u -> {
                    u.setLastLoginAt(Instant.now());
                    if (displayName != null && !displayName.isBlank()) {
                        u.setDisplayName(displayName);
                    }
                    return userRepo.save(u);
                })
                .orElseGet(() -> {
                    UserAccount u = UserAccount.create(deviceId, displayName);
                    u.setLastLoginAt(Instant.now());
                    return userRepo.save(u);
                });
    }

    public UserAccount getById(String userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    @Transactional
    public UserAccount updatePreferences(String userId, UserAccount.RiskPreference riskPreference, int automationLevel) {
        UserAccount u = getById(userId);
        if (riskPreference != null) u.setRiskPreference(riskPreference);
        if (automationLevel >= 0 && automationLevel <= 3) u.setAutomationLevel(automationLevel);
        return userRepo.save(u);
    }
}