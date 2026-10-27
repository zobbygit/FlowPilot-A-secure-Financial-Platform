package com.flowpilot.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All FlowPilot-specific settings. Secrets come from environment variables only. */
@Validated
@ConfigurationProperties(prefix = "flowpilot")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        Cors cors,
        Cookie cookie,
        Sessions sessions,
        Tokens tokens,
        Brevo brevo,
        Admin admin,
        Cloudinary cloudinary) {

    public record Cors(List<String> allowedOrigins) {}

    public record Cookie(String name, boolean secure, String sameSite) {}

    public record Sessions(Duration idleTimeout, Duration absoluteTimeout) {}

    public record Tokens(Duration verifyEmailTtl, Duration resetPasswordTtl) {}

    public record Brevo(boolean enabled, String apiKey, String senderEmail, String senderName) {}

    public record Admin(String bootstrapEmail, String bootstrapPassword, String bootstrapName, boolean bootstrapReset) {}

    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {
        public boolean configured() {
            return cloudName != null && !cloudName.isBlank() && apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
        }
    }
}