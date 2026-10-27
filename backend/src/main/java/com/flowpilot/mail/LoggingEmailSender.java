package com.flowpilot.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Used only when BREVO_API_KEY is empty (or BREVO_ENABLED=false). It does NOT send anything: it prints the
 * message (including the one-time link) to the log so you can complete flows locally.
 * The "prod" profile refuses to start unless Brevo is enabled, so this can never run in production.
 */
@Component
@ConditionalOnExpression("'${flowpilot.brevo.api-key:}'.isEmpty() or '${flowpilot.brevo.enabled:true}' == 'false'")
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @jakarta.annotation.PostConstruct
    void announce() {
        log.warn("EMAIL DELIVERY: LOG-ONLY mode. No real email will be sent because BREVO_API_KEY is empty (or BREVO_ENABLED=false). "
                + "If you set it in a .env file, make sure the file is in the backend folder or the project root and restart the API.");
    }

    @Override
    public void send(OutgoingEmail mail) {
        log.warn("DEV MODE - email NOT sent (Brevo disabled). To={} Subject=\"{}\"\n{}",
                mail.toEmail(), mail.subject(), mail.text());
    }
}