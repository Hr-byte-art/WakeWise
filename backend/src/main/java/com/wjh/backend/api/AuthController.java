package com.wjh.backend.api;

import com.wjh.backend.domain.user.UserAccount;
import com.wjh.backend.domain.user.UserService;
import com.wjh.backend.infrastructure.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest req) {
        UserAccount user = userService.registerOrLogin(req.deviceId(), req.displayName());
        return ApiResponse.ok(new AuthResponse(user.getId(), user.getDeviceId(), user.getDisplayName()));
    }

    public record AuthRequest(@NotBlank String deviceId, String displayName) {}
    public record AuthResponse(String userId, String deviceId, String displayName) {}
}