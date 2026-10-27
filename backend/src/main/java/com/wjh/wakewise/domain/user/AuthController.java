package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserProfile> me() {
        return ApiResponse.ok(AuthDtos.UserProfile.from(userService.requireUser(AuthContext.currentUserId())));
    }

    /** 更新常驻地址（家）：真实交通 ETA 的出发地；传空串清除。 */
    @PatchMapping("/me")
    public ApiResponse<AuthDtos.UserProfile> updateMe(@RequestBody AuthDtos.UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateHomeAddress(AuthContext.currentUserId(),
                request == null ? null : request.homeAddress()));
    }
}
