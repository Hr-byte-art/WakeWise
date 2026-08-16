package com.wjh.backend.domain.user;

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
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        var user = userService.registerOrLogin(req.deviceId(), req.displayName());
        return ApiResponse.ok(new UserResponse(
                user.getId(), user.getDeviceId(), user.getDisplayName(),
                user.getTimezone(), user.getRiskPreference().name(),
                user.getAutomationLevel(), user.getVersion()
        ));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@RequestParam String userId) {
        var user = userService.getById(userId);
        return ApiResponse.ok(new UserResponse(
                user.getId(), user.getDeviceId(), user.getDisplayName(),
                user.getTimezone(), user.getRiskPreference().name(),
                user.getAutomationLevel(), user.getVersion()
        ));
    }

    public record RegisterRequest(@NotBlank String deviceId, String displayName) {}
    public record UserResponse(String id, String deviceId, String displayName,
                               String timezone, String riskPreference,
                               int automationLevel, long version) {}
}