package com.flowpilot.auth;

import com.flowpilot.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response DTOs. Entities are never serialised directly. */
public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy.") boolean acceptTerms) {}

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String password) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}

    public record MessageResponse(String message) {}

    public record MeResponse(UUID id, String email, String fullName, Role role, boolean emailVerified,
                             String avatarUrl, String themePreference, boolean onboardingCompleted) {}

    public record SessionView(UUID id, boolean current, String userAgent, String ipAddress, Instant createdAt,
                              Instant lastActiveAt, Instant expiresAt, String status) {}
}
