package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.config.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
}
