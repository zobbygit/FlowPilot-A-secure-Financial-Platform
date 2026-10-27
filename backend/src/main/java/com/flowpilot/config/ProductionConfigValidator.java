package com.flowpilot.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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

        if (!props.cookie().secure()) {
            problems.add("FLOWPILOT_COOKIE_SECURE must be true");
        }

        String baseUrl = props.publicBaseUrl();

        if (isBlank(baseUrl)) {
            // Allowed during backend-first deployment.
            // Configure the real public URL when needed.
        } else if (!isHttpsUrl(baseUrl)) {
            problems.add(
                "FLOWPILOT_PUBLIC_BASE_URL must be a valid HTTPS URL"
            );
        }

List<String> origins = props.cors().allowedOrigins();

if (origins == null || origins.isEmpty()) {
    problems.add("FLOWPILOT_CORS_ALLOWED_ORIGINS must be set");
} else {
    for (String origin : origins) {
        if (isBlank(origin) || !isHttpsOrigin(origin.trim())) {
            problems.add(
                "FLOWPILOT_CORS_ALLOWED_ORIGINS must contain " +
                "explicit HTTPS origins without wildcards"
            );
            break;
        }
    }
}

        if ("None".equalsIgnoreCase(props.cookie().sameSite())
                && !props.cookie().secure()) {
            problems.add("SameSite=None requires secure cookies");
        }

        var brevo = props.brevo();

        if (!brevo.enabled()) {
            problems.add("BREVO_ENABLED must not be false");
        }

        if (isBlank(brevo.apiKey())) {
            problems.add("BREVO_API_KEY must be set");
        }

        if (isBlank(brevo.senderEmail())) {
            problems.add("BREVO_SENDER_EMAIL must be set");
        }

        if (props.cloudinary() == null
                || !props.cloudinary().configured()) {
            problems.add(
                "CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and " +
                "CLOUDINARY_API_SECRET must be set"
            );
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "Invalid production configuration: "
                    + String.join("; ", problems)
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !uri.getHost().isBlank()
                && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isHttpsOrigin(String value) {
        if (value.contains("*")) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());

            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !uri.getHost().isBlank()
                && uri.getUserInfo() == null
                && uri.getRawPath() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}