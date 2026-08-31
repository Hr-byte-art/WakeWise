package com.wjh.wakewise.domain.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 64) String password,
            @NotBlank @Size(max = 80) String displayName,
            String timezone,
            String city) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserProfile(
            String id,
            String email,
            String displayName,
            String timezone,
            String city,
            String riskPreference,
            int automationLevel) {

        public static UserProfile from(UserAccount user) {
            return new UserProfile(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getTimezone(), user.getCity(),
                    user.getRiskPreference().name(), user.getAutomationLevel());
        }
    }

    public record AuthResponse(String token, UserProfile user) {
    }
}
