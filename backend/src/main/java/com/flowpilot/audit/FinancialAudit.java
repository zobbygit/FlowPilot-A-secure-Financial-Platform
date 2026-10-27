package com.flowpilot.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes the user-visible financial audit trail IN THE CALLER'S TRANSACTION. Values are short, structured summaries
 * (amounts, statuses, names) - never secrets, tokens or free-text notes.
 */
@Component
public class FinancialAudit {
    private final JdbcTemplate jdbc;

    public FinancialAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID owner, UUID actor, String action, String entityType, UUID entityId, String summary, String prev, String next) {
        jdbc.update("insert into financial_audit (owner_id, actor_id, action, entity_type, entity_id, summary, prev_value, new_value) values (?, ?, ?, ?, ?, ?, ?, ?)",
                owner, actor, action, entityType, entityId, trunc(summary, 200), trunc(prev, 300), trunc(next, 300));
    }

    private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
