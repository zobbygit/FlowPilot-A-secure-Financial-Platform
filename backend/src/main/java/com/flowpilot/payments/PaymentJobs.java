package com.flowpilot.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expiry sweeps. Single atomic statements, so two instances running at once cannot double-process a row. */
@Component
public class PaymentJobs {
    private static final Logger log = LoggerFactory.getLogger(PaymentJobs.class);
    private final JdbcTemplate jdbc;

    public PaymentJobs(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expire() {
        int p = jdbc.update("with u as (update payments set status = 'CANCELLED', failure_reason = 'Expired before confirmation', updated_at = now() where status = 'PENDING' and expires_at < now() returning id) "
                + "insert into payment_events (payment_id, from_status, to_status, note) select id, 'PENDING', 'CANCELLED', 'Expired before confirmation' from u");
        int r = jdbc.update("update payment_requests set status = 'EXPIRED' where status = 'OPEN' and expires_at < now()");
        if (p + r > 0) log.info("Expired {} pending payments and {} requests", p, r);
    }
}
