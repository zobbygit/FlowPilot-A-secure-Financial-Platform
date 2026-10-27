package com.flowpilot.admin;

import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime-editable platform settings. Only whitelisted keys exist, each with a backend validator, and every change is
 * audited with old and new values. Security-critical settings (cookies, session lifetimes, CORS, secrets) are deliberately
 * NOT here: they are environment-only so they cannot be changed from the UI by accident or by a compromised account.
 */
@Service
public class SettingsService {
    public record Setting(String key, String value, String description, Instant updatedAt) {}

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    static {
        DESCRIPTIONS.put("currencies.supported", "Currencies users can choose (comma-separated ISO 4217 codes, e.g. USD,EUR).");
        DESCRIPTIONS.put("payment.max_amount", "Largest single simulated payment or request.");
        DESCRIPTIONS.put("payment.pending_ttl_minutes", "Minutes a pending simulated payment waits for confirmation (1–120).");
        DESCRIPTIONS.put("support.reopen_days", "Days after resolution during which a user can reopen a ticket (0–90).");
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0;

    public SettingsService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public List<Setting> all() {
        return jdbc.query("select key, value, updated_at from platform_settings order by key", (rs, i) -> new Setting(rs.getString(1), rs.getString(2), DESCRIPTIONS.getOrDefault(rs.getString(1), ""), rs.getTimestamp(3).toInstant()));
    }

    public String get(String key, String fallback) {
        if (System.currentTimeMillis() - loadedAt > 30_000) {   // short cache: changes apply within 30 seconds, no restart
            Map<String, String> fresh = new HashMap<>();
            jdbc.query("select key, value from platform_settings", rs -> { fresh.put(rs.getString(1), rs.getString(2)); });
            cache.clear(); cache.putAll(fresh); loadedAt = System.currentTimeMillis();
        }
        return cache.getOrDefault(key, fallback);
    }

    public BigDecimal decimal(String key, String fallback) { return new BigDecimal(get(key, fallback)); }
    public int integer(String key, int fallback) { return Integer.parseInt(get(key, String.valueOf(fallback))); }
    public Set<String> currencies() { return new LinkedHashSet<>(Arrays.asList(get("currencies.supported", "USD").split(","))); }

    @Transactional
    public Setting update(UUID adminId, String key, String value, ClientInfo ci) {
        if (!DESCRIPTIONS.containsKey(key)) throw ApiException.notFound("Unknown or protected setting.");
        String v = value == null ? "" : value.trim();
        validate(key, v);
        String old = jdbc.queryForObject("select value from platform_settings where key = ?", String.class, key);
        jdbc.update("update platform_settings set value = ?, updated_by = ?, updated_at = now() where key = ?", v, adminId, key);
        loadedAt = 0;
        audit.record(Role.ADMIN, "SETTING_CHANGED", true, adminId, null, ci, key + ": " + old + " -> " + v);
        return all().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    static void validate(String key, String v) {
        switch (key) {
            case "currencies.supported" -> {
                String[] codes = v.split(",");
                if (codes.length < 1 || codes.length > 30 || Arrays.stream(codes).anyMatch(c -> !c.matches("[A-Z]{3}") || !isIso(c))) throw ApiException.badRequest("INVALID_SETTING", "Use 1–30 valid 3-letter currency codes separated by commas.");
                if (new HashSet<>(Arrays.asList(codes)).size() != codes.length) throw ApiException.badRequest("INVALID_SETTING", "Currency codes must be unique.");
            }
            case "payment.max_amount" -> { try { BigDecimal b = new BigDecimal(v); if (b.signum() <= 0 || b.compareTo(new BigDecimal("10000000")) > 0 || b.scale() > 2) throw new NumberFormatException(); } catch (NumberFormatException e) { throw ApiException.badRequest("INVALID_SETTING", "Enter an amount between 0.01 and 10,000,000 (up to 2 decimals)."); } }
            case "payment.pending_ttl_minutes" -> intRange(v, 1, 120);
            case "support.reopen_days" -> intRange(v, 0, 90);
            default -> throw ApiException.notFound("Unknown or protected setting.");
        }
    }

    private static boolean isIso(String c) { try { return Currency.getInstance(c) != null; } catch (IllegalArgumentException e) { return false; } }

    private static void intRange(String v, int lo, int hi) {
        try { int n = Integer.parseInt(v); if (n < lo || n > hi) throw new NumberFormatException(); } catch (NumberFormatException e) { throw ApiException.badRequest("INVALID_SETTING", "Enter a whole number from " + lo + " to " + hi + "."); }
    }
}
