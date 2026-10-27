package com.flowpilot.notify;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    public record NotificationView(UUID id, String type, String title, String body, String link, boolean read, Instant createdAt) {}
    public record PageOf(List<NotificationView> items, long unread, int page, int size, long total) {}
    public record Prefs(boolean bills, boolean budgets, boolean lowBalance, boolean payments, boolean automation, boolean support, String lowBalanceThreshold) {}

    private static final Map<String, String> TYPE_COLUMN = Map.of("BILL", "bills", "BUDGET", "budgets", "LOW_BALANCE", "low_balance",
            "PAYMENT", "payments", "AUTOMATION", "automation", "SUPPORT", "support");

    private final JdbcTemplate jdbc;
    private final LiveUpdateRegistry live;

    public NotificationService(JdbcTemplate jdbc, LiveUpdateRegistry live) {
        this.jdbc = jdbc;
        this.live = live;
    }

    /** Idempotent: the same (user, dedupeKey) can only ever create one notification. Returns true if a new one was created. */
    public boolean notify(UUID userId, String type, String title, String body, String link, String dedupeKey) {
        if (!enabled(userId, type)) return false;
        String key = dedupeKey == null ? UUID.randomUUID().toString() : dedupeKey;
        int n = jdbc.update("insert into notifications (user_id, type, title, body, link, dedupe_key) values (?, ?, ?, ?, ?, ?) on conflict (user_id, dedupe_key) do nothing",
                userId, type, title, body, link, key);
        if (n == 1) live.push(userId, "notifications");
        return n == 1;
    }

    /** SECURITY (and SYSTEM) notifications cannot be switched off. */
    private boolean enabled(UUID userId, String type) {
        String col = TYPE_COLUMN.get(type);
        if (col == null) return true;
        List<Boolean> v = jdbc.query("select " + col + " from notification_preferences where user_id = ?", (rs, i) -> rs.getBoolean(1), userId);   // col comes from the constant map above
        return v.isEmpty() || v.get(0);
    }

    public PageOf list(UUID userId, boolean unreadOnly, int page, int size) {
        int s = Math.min(Math.max(size, 1), 50), p = Math.max(page, 0);
        String where = "user_id = ?" + (unreadOnly ? " and read_at is null" : "");
        Long total = jdbc.queryForObject("select count(*) from notifications where " + where, Long.class, userId);
        List<NotificationView> items = jdbc.query("select * from notifications where " + where + " order by created_at desc limit ? offset ?",
                (rs, i) -> new NotificationView(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"), rs.getString("body"), rs.getString("link"),
                        rs.getTimestamp("read_at") != null, rs.getTimestamp("created_at").toInstant()), userId, s, (long) p * s);
        return new PageOf(items, unread(userId), p, s, total == null ? 0 : total);
    }

    public long unread(UUID userId) {
        Long n = jdbc.queryForObject("select count(*) from notifications where user_id = ? and read_at is null", Long.class, userId);
        return n == null ? 0 : n;
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        if (jdbc.update("update notifications set read_at = coalesce(read_at, now()) where id = ? and user_id = ?", id, userId) == 0) throw ApiException.notFound("Notification not found.");
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return jdbc.update("update notifications set read_at = now() where user_id = ? and read_at is null", userId);
    }

    public Prefs prefs(UUID userId) {
        List<Prefs> v = jdbc.query("select * from notification_preferences where user_id = ?", (rs, i) -> new Prefs(rs.getBoolean("bills"), rs.getBoolean("budgets"),
                rs.getBoolean("low_balance"), rs.getBoolean("payments"), rs.getBoolean("automation"), rs.getBoolean("support"),
                rs.getBigDecimal("low_balance_threshold") == null ? null : rs.getBigDecimal("low_balance_threshold").toPlainString()), userId);
        return v.isEmpty() ? new Prefs(true, true, true, true, true, true, null) : v.get(0);
    }

    @Transactional
    public Prefs savePrefs(UUID userId, Prefs p) {
        BigDecimal threshold = p.lowBalanceThreshold() == null || p.lowBalanceThreshold().isBlank() ? null : new BigDecimal(p.lowBalanceThreshold());
        if (threshold != null && (threshold.signum() < 0 || threshold.scale() > 4)) throw ApiException.badRequest("INVALID_THRESHOLD", "Enter a non-negative amount with up to 4 decimals.");
        jdbc.update("insert into notification_preferences (user_id, bills, budgets, low_balance, payments, automation, support, low_balance_threshold) values (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (user_id) do update set bills = excluded.bills, budgets = excluded.budgets, low_balance = excluded.low_balance, payments = excluded.payments, "
                        + "automation = excluded.automation, support = excluded.support, low_balance_threshold = excluded.low_balance_threshold",
                userId, p.bills(), p.budgets(), p.lowBalance(), p.payments(), p.automation(), p.support(), threshold);
        return prefs(userId);
    }
}
