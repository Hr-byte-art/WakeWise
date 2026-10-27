package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.config.JwtService;
import com.wjh.wakewise.domain.travel.AmapClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AmapClient amapClient;
    private final CredentialService credentialService;

    public UserService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AmapClient amapClient,
                       CredentialService credentialService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.amapClient = amapClient;
        this.credentialService = credentialService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "该邮箱已注册");
        }
        UserAccount user = new UserAccount();
        user.setId(Ids.newId());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        user.setCity(request.city());
        userRepository.save(user);
        return new AuthDtos.AuthResponse(jwtService.issue(user.getId(), user.getEmail()),
                AuthDtos.UserProfile.from(user));
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED, "邮箱或密码错误"));
        if (user.getStatus() != UserAccount.UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "邮箱或密码错误");
        }
        user.setLastLoginAt(Instant.now());
        return new AuthDtos.AuthResponse(jwtService.issue(user.getId(), user.getEmail()),
                AuthDtos.UserProfile.from(user));
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    /**
     * 更新常驻地址（家）：地址变更时顺带做地理编码落坐标缓存（best-effort，
     * 凭据未启用/失败留空，估算链路会现查兜底）；空串清除地址与坐标。
     */
    @Transactional
    public AuthDtos.UserProfile updateHomeAddress(String userId, String homeAddress) {
        UserAccount user = requireUser(userId);
        if (homeAddress == null || homeAddress.isBlank()) {
            user.setHomeAddress(null);
            user.setHomeLng(null);
            user.setHomeLat(null);
            return AuthDtos.UserProfile.from(user);
        }
        user.setHomeAddress(homeAddress.trim());
        // 地理编码用用户生效凭据（BYOK ＞ 服务端默认）；凭据未启用时 geocode 返回 empty、坐标留空
        BigDecimal[] location = AmapClient.parseLocation(
                amapClient.geocode(user.getHomeAddress(), credentialService.amapFor(userId).key()).orElse(null));
        user.setHomeLng(location == null ? null : location[0]);
        user.setHomeLat(location == null ? null : location[1]);
        return AuthDtos.UserProfile.from(user);
    }
}
