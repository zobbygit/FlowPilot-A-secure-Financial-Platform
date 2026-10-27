package com.flowpilot.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails startup in the "prod" profile when security-critical configuration is missing or unsafe.
 * Error messages name the offending property only - never its value.
 */
@Component
@Profile("prod")
public class ProductionConfigValidator implements InitializingBean {

    private final AppProperties props;

    public ProductionConfigValidator(AppProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();
        if (!props.cookie().secure()) problems.add("FLOWPILOT_COOKIE_SECURE must be true");
        if (!props.publicBaseUrl().startsWith("https://")) problems.add("FLOWPILOT_PUBLIC_BASE_URL must be https");
        List<String> origins = props.cors().allowedOrigins();
        if (origins == null || origins.isEmpty()) problems.add("FLOWPILOT_CORS_ALLOWED_ORIGINS must be set");
        else if (origins.stream().anyMatch(o -> o.contains("*") || !o.startsWith("https://")))
            problems.add("FLOWPILOT_CORS_ALLOWED_ORIGINS must be explicit https origins (no wildcards)");
        if ("None".equalsIgnoreCase(props.cookie().sameSite()) && !props.cookie().secure())
            problems.add("SameSite=None requires secure cookies");
        var b = props.brevo();
        if (!b.enabled()) problems.add("BREVO_ENABLED must not be false");
        if (isBlank(b.apiKey())) problems.add("BREVO_API_KEY must be set");
        if (isBlank(b.senderEmail())) problems.add("BREVO_SENDER_EMAIL must be set");
        if (props.cloudinary() == null || !props.cloudinary().configured()) problems.add("CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET must be set");
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", problems));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}