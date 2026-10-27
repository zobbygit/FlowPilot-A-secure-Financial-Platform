package com.flowpilot.jobs;

import com.flowpilot.repo.AuthSessionRepository;
import com.flowpilot.repo.EmailTokenRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention clean-up. Idempotent deletes, so running on several instances at once is harmless.
 * (Jobs that must run exactly once - reminders, recurring payments - will use a DB lock; see docs/STATUS.md.)
 */
@Component
public class MaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);
    private final EmailTokenRepository tokens;
    private final AuthSessionRepository sessions;

    public MaintenanceJobs(EmailTokenRepository tokens, AuthSessionRepository sessions) {
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        int t = tokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
        int s = sessions.deleteDeadBefore(Instant.now().minus(Duration.ofDays(30)));   // device-history retention: 30 days
        log.info("Retention purge removed {} expired tokens and {} dead sessions", t, s);
    }
}
