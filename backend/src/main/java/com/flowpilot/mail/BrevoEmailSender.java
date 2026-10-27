package com.flowpilot.mail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.flowpilot.config.AppProperties;

/**
 * Brevo transactional email over the HTTP API (no SMTP). The destination host is a constant
 * (SSRF-safe by construction) and the API key never leaves the backend or the logs.
 */
@Component
@ConditionalOnExpression("!'${flowpilot.brevo.api-key:}'.isEmpty() and '${flowpilot.brevo.enabled:true}' != 'false'")
public class BrevoEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final String BASE_URL = "https://api.brevo.com/v3";

    private final RestClient client;
    private final AppProperties.Brevo cfg;

    public BrevoEmailSender(AppProperties props) {
        this.cfg = props.brevo();
        if (cfg.senderEmail() == null || cfg.senderEmail().isBlank()) throw new IllegalStateException("BREVO_SENDER_EMAIL must be set to a sender address that is verified in Brevo.");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("api-key", cfg.apiKey())
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("EMAIL DELIVERY: Brevo is ACTIVE. Real emails will be sent from {}.", cfg.senderEmail());
    }

    @Override
    public void send(OutgoingEmail mail) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", cfg.senderName(), "email", cfg.senderEmail()),
                "to", List.of(Map.of("email", mail.toEmail(), "name", mail.toName())),
                "subject", mail.subject(),
                "htmlContent", mail.html(),
                "textContent", mail.text());

        // One retry, only for transient failures (429/5xx/network). Sending a verification or reset
        // email twice is harmless because older tokens are revoked when a new one is issued.
        for (int attempt = 1; ; attempt++) {
            try {
                client.post().uri("/smtp/email").contentType(MediaType.APPLICATION_JSON).body(payload)
                        .retrieve().toBodilessEntity();
                log.info("Brevo accepted a transactional email request (subject type kept out of logs)");
                return;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Brevo returned HTTP {}: {}", status, abbreviate(e.getResponseBodyAsString()));   // e.g. "sender not valid" / "unauthorized" / "account not activated"
                boolean transientFailure = status == 429 || status >= 500;
                if (transientFailure && attempt < 2) { pause(); continue; }
                throw new EmailDeliveryException("Brevo rejected the email request (HTTP " + status + ")", status, e);
            } catch (ResourceAccessException e) {
                if (attempt < 2) { pause(); continue; }
                throw new EmailDeliveryException("Brevo unreachable or timed out", -1, e);
            }
        }
    }

    private static String abbreviate(String s) { return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s; }

    private static void pause() {
        try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}