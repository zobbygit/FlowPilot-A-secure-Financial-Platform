package com.flowpilot.account;

import com.flowpilot.account.AccountDtos.*;
import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.domain.Role;
import com.flowpilot.repo.UserRepository;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.security.SessionService;
import com.flowpilot.security.TokenService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final TokenService tokens;
    private final AuditService audit;
    private final RateLimiter limiter;

    public ProfileService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder, SessionService sessions, TokenService tokens, AuditService audit, RateLimiter limiter) {
        this.jdbc = jdbc; this.users = users; this.encoder = encoder; this.sessions = sessions; this.tokens = tokens; this.audit = audit; this.limiter = limiter;
    }

    public ProfileView profile(UUID userId) {
        return jdbc.query("select u.email, p.* from user_profiles p join users u on u.id = p.user_id where p.user_id = ?", (rs, i) -> new ProfileView(
                rs.getString("email"), rs.getString("full_name"), rs.getString("preferred_currency"), rs.getString("timezone"), rs.getString("locale"),
                rs.getString("theme_preference"), rs.getString("number_format"), rs.getString("date_format"), goals(rs.getString("financial_goals")),
                rs.getString("avatar_url"), rs.getTimestamp("onboarding_completed_at") != null), userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    @Transactional
    public ProfileView update(UUID userId, ProfileRequest r) {
        requireZone(r.timezone());
        jdbc.update("update user_profiles set full_name = ?, preferred_currency = ?, timezone = ?, locale = ?, theme_preference = ?, number_format = ?, date_format = ?, updated_at = now(), version = version + 1 where user_id = ?",
                r.fullName().trim(), r.preferredCurrency(), r.timezone(), r.locale(), r.themePreference(), r.numberFormat(), r.dateFormat(), userId);
        return profile(userId);
    }

    /** Full personal-data download. Re-authentication required; secrets (hashes, token hashes) are never included. */
    public Map<String, Object> export(AuthPrincipal p, String password, ClientInfo ci) {
        limiter.check("export", p.userId().toString(), 5, Duration.ofHours(1));
        reauth(p.userId(), password);
        UUID u = p.userId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("notice", "Personal records exported from FlowPilot. These are personal records, not official tax documents.");
        out.put("profile", rows("select u.email, p.full_name, p.preferred_currency, p.timezone, p.locale, p.theme_preference, p.financial_goals, p.avatar_url, p.onboarding_completed_at from user_profiles p join users u on u.id = p.user_id where p.user_id = ?", u));
        out.put("accounts", rows("select id, name, type, currency, current_balance, allow_negative, archived_at, created_at from accounts where user_id = ? order by created_at", u));
        out.put("transactions", rows("select id, type, status, occurred_on, description, merchant, category_id, payment_method, notes, reverses_transaction_id, created_at from transactions where user_id = ? order by occurred_on, created_at", u));
        out.put("ledgerEntries", rows("select id, transaction_id, account_id, amount, currency, created_at from ledger_entries where user_id = ? order by created_at", u));
        out.put("categories", rows("select id, name, kind, color, archived_at from categories where user_id = ?", u));
        out.put("budgets", rows("select id, name, period_type, start_date, end_date, category_id, limit_amount, currency, rollover, alert_thresholds, archived_at from budgets where user_id = ?", u));
        out.put("bills", rows("select id, name, kind, amount, currency, frequency, next_due_date, status, notes, last_paid_on, archived_at from bills where user_id = ?", u));
        out.put("savingsGoals", rows("select id, name, goal_type, target_amount, target_date, currency, status from savings_goals where user_id = ?", u));
        out.put("goalContributions", rows("select id, goal_id, kind, amount, transaction_id, created_at from goal_contributions where user_id = ?", u));
        out.put("sessionsAndDevices", rows("select created_at, last_active_at, expires_at, revoked_at, revoked_reason, user_agent, ip_address from auth_sessions where user_id = ? order by created_at desc", u));
        audit.record(p.role(), "DATA_EXPORTED", true, u, p.sessionId(), ci, null);
        return out;
    }

    /**
     * Account deletion policy: the person is de-identified and signed out everywhere; planning items are archived;
     * the immutable ledger is RETAINED (de-identified) so financial integrity and legal retention obligations hold.
     * Nothing financial is cascade-deleted.
     */
    @Transactional
    public void deleteAccount(AuthPrincipal p, DeleteAccountRequest r, ClientInfo ci) {
        if (p.role() != Role.USER) throw ApiException.forbidden("NOT_ALLOWED", "Administrator accounts cannot be deleted here.");
        if (!"DELETE".equals(r.confirmation())) throw ApiException.badRequest("CONFIRMATION_REQUIRED", "Type DELETE to confirm.");
        limiter.check("delete-account", p.userId().toString(), 3, Duration.ofHours(1));
        reauth(p.userId(), r.password());
        UUID u = p.userId();
        jdbc.update("update users set email = ?, password_hash = ?, status = 'DELETED', email_verified_at = null, updated_at = now() where id = ?",
                "deleted-" + u + "@deleted.invalid", encoder.encode(tokens.issue().raw()), u);
        jdbc.update("update user_profiles set full_name = 'Deleted user', avatar_url = null, avatar_public_id = null, financial_goals = null, onboarding_draft = null where user_id = ?", u);
        jdbc.update("update bills set archived_at = now() where user_id = ? and archived_at is null", u);
        jdbc.update("update budgets set archived_at = now() where user_id = ? and archived_at is null", u);
        jdbc.update("update savings_goals set status = 'ARCHIVED' where user_id = ? and status <> 'ARCHIVED'", u);
        jdbc.update("delete from notifications where user_id = ?", u);
        jdbc.update("delete from user_preferred_categories where user_id = ?", u);
        sessions.revokeAll(u, "ACCOUNT_DELETED");
        audit.record(Role.USER, "ACCOUNT_DELETED", true, u, null, ci, "ledger retained de-identified");
    }

    private void reauth(UUID userId, String password) {
        String hash = users.findById(userId).map(x -> x.getPasswordHash()).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please sign in."));
        if (!encoder.matches(password, hash)) throw ApiException.badRequest("INVALID_PASSWORD", "That password is incorrect.");
    }

    static void requireZone(String tz) {
        if (!ZoneId.getAvailableZoneIds().contains(tz)) throw ApiException.badRequest("INVALID_TIMEZONE", "Choose a valid timezone, e.g. Europe/London.");
    }

    private List<Map<String, Object>> rows(String sql, UUID user) {
        List<Map<String, Object>> raw = jdbc.queryForList(sql, user);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.forEach((k, v) -> m.put(k, v == null ? null : v instanceof BigDecimal b ? b.toPlainString() : v instanceof Boolean || v instanceof Number ? v : v.toString()));
            out.add(m);
        }
        return out;
    }

    private static List<String> goals(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split("\u001f")).filter(s -> !s.isBlank()).toList();
    }

    static String joinGoals(List<String> goals) {
        return goals == null ? null : String.join("\u001f", goals.stream().map(g -> g.replace("\u001f", " ").trim()).filter(g -> !g.isEmpty()).toList());
    }
}
