package com.flowpilot.admin;

import com.flowpilot.admin.AdminDtos.*;
import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.Role;
import com.flowpilot.notify.LiveUpdateRegistry;
import com.flowpilot.payments.PaymentService;
import com.flowpilot.reports.ReportService.ReportTable;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.security.SessionService;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational tooling. Everything here is reachable only through /api/admin/** (ROLE_ADMIN, enforced by the backend on every
 * request). Reads are aggregate-first; private financial detail is only available through a reasoned, time-boxed, audited
 * investigation. Every state-changing action writes an ADMIN-scope audit event (append-only table).
 */
@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SessionService sessions;
    private final PaymentService payments;
    private final LiveUpdateRegistry live;

    public AdminService(JdbcTemplate jdbc, AuditService audit, SessionService sessions, PaymentService payments, LiveUpdateRegistry live) {
        this.jdbc = jdbc; this.audit = audit; this.sessions = sessions; this.payments = payments; this.live = live;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Map<String, Long> users = counts("select status, count(*) from users where role = 'USER' group by status");
        Map<String, Long> pay = counts("select status, count(*) from payments group by status");
        List<Map<String, String>> volume = jdbc.query("select currency, coalesce(sum(amount), 0) as total, count(*) as n from payments where status = 'SUCCESSFUL' and created_at > now() - interval '7 days' group by currency order by currency",
                (rs, i) -> Map.of("currency", rs.getString(1), "amount", rs.getBigDecimal(2).toPlainString(), "count", String.valueOf(rs.getLong(3))));
        List<DayPoint> activity = jdbc.query("select d::date as day, "
                + "(select count(*) from transactions t where t.created_at::date = d::date and t.type in ('INCOME','EXPENSE','TRANSFER') and t.reverses_transaction_id is null) as tx, "
                + "(select count(*) from payments p where p.created_at::date = d::date) as pay, (select count(*) from users u where u.role = 'USER' and u.created_at::date = d::date) as nu "
                + "from generate_series(current_date - 13, current_date, interval '1 day') d order by d",
                (rs, i) -> new DayPoint(rs.getObject(1, LocalDate.class), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
        Double avgResp = jdbc.queryForObject("select avg(extract(epoch from (first_admin_reply_at - created_at)) / 3600.0) from support_tickets where first_admin_reply_at is not null and created_at > now() - interval '30 days'", Double.class);
        return new Dashboard(users, n("select count(*) from users where role = 'USER' and created_at > now() - interval '7 days'"), n("select count(*) from users where role = 'USER' and created_at > now() - interval '30 days'"),
                n("select count(*) from transactions where created_at > now() - interval '24 hours' and type in ('INCOME','EXPENSE','TRANSFER') and reverses_transaction_id is null"),
                n("select count(*) from transactions where created_at > now() - interval '7 days' and type in ('INCOME','EXPENSE','TRANSFER') and reverses_transaction_id is null"),
                pay, n("select count(*) from payments where status = 'FAILED' and created_at > now() - interval '24 hours'"), volume,
                n("select count(*) from payment_disputes where status = 'OPEN'"), n("select count(*) from payment_refunds where created_at > now() - interval '7 days'"),
                n("select count(*) from support_tickets where status in ('OPEN','IN_PROGRESS','AWAITING_USER')"), n("select count(*) from support_tickets where status in ('OPEN','IN_PROGRESS','AWAITING_USER') and priority = 'URGENT'"),
                n("select count(*) from support_tickets where status = 'OPEN' and assigned_admin_id is null"), avgResp == null ? null : Math.round(avgResp * 10) / 10.0,
                health(), recent(10), activity);
    }

    private Health health() {
        long t0 = System.nanoTime();
        jdbc.queryForObject("select 1", Integer.class);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Runtime rt = Runtime.getRuntime();
        List<String> versions = jdbc.query("select (select version from flyway_schema_history where success order by installed_rank desc limit 1)", (rs, n) -> rs.getString(1));
        String version = versions.isEmpty() || versions.get(0) == null ? "unknown" : versions.get(0);
        long stale = n("select (select count(*) from payments where status = 'PENDING' and expires_at < now() - interval '5 minutes') + (select count(*) from payment_requests where status = 'OPEN' and expires_at < now() - interval '5 minutes')");
        List<String> checks = new ArrayList<>();
        checks.add(ms < 500 ? "Database responding" : "Database slow (" + ms + " ms)");
        checks.add(stale == 0 ? "Expiry jobs up to date" : stale + " expired items not yet swept — check the scheduler");
        return new Health(ms, live.connectionCount(), (rt.totalMemory() - rt.freeMemory()) / 1048576, rt.maxMemory() / 1048576, ManagementFactory.getRuntimeMXBean().getUptime() / 60000, version, stale, checks);
    }

    // ------------------------------------------------------------------ users

    public UserPage users(String q, String status, int page, int size) {
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        StringBuilder w = new StringBuilder(" where u.role = 'USER'"); List<Object> a = new ArrayList<>();
        if (status != null && status.matches("PENDING_VERIFICATION|ACTIVE|SUSPENDED|DELETION_REQUESTED|DELETED")) { w.append(" and u.status = ?"); a.add(status); }
        if (q != null && !q.isBlank()) { String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; w.append(" and (u.email like ? escape '\\' or lower(p.full_name) like ? escape '\\')"); a.add(like); a.add(like); }
        Long total = jdbc.queryForObject("select count(*) from users u join user_profiles p on p.user_id = u.id" + w, Long.class, a.toArray());
        List<Object> pa = new ArrayList<>(a); pa.add(sz); pa.add((long) pg * sz);
        List<UserRow> rows = jdbc.query("select u.id, u.email, p.full_name, u.status, u.created_at, u.email_verified_at, (select max(s.created_at) from auth_sessions s where s.user_id = u.id) as last_login, "
                + "(select count(*) from accounts x where x.user_id = u.id and x.archived_at is null) as accts, (select count(*) from support_tickets t where t.user_id = u.id and t.status in ('OPEN','IN_PROGRESS','AWAITING_USER')) as tix "
                + "from users u join user_profiles p on p.user_id = u.id" + w + " order by u.created_at desc limit ? offset ?",
                (rs, i) -> new UserRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) != null, rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getLong(8), rs.getLong(9)), pa.toArray());
        return new UserPage(rows, pg, sz, total == null ? 0 : total);
    }

    @Transactional
    public void suspend(AuthPrincipal admin, UUID userId, String reason, ClientInfo ci) {
        requireUser(admin, userId);
        if (jdbc.update("update users set status = 'SUSPENDED', updated_at = now() where id = ? and role = 'USER' and status = 'ACTIVE'", userId) == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_SUSPENDABLE", "Only active accounts can be suspended.");
        sessions.revokeAll(userId, "ACCOUNT_SUSPENDED");
        audit.record(Role.ADMIN, "USER_SUSPENDED", true, admin.userId(), admin.sessionId(), ci, "user " + userId + "; reason: " + reason.trim());
    }

    @Transactional
    public void reactivate(AuthPrincipal admin, UUID userId, String reason, ClientInfo ci) {
        requireUser(admin, userId);
        if (jdbc.update("update users set status = case when email_verified_at is null then 'PENDING_VERIFICATION' else 'ACTIVE' end, failed_login_attempts = 0, locked_until = null, updated_at = now() where id = ? and role = 'USER' and status = 'SUSPENDED'", userId) == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_SUSPENDED", "Only suspended accounts can be reactivated.");
        audit.record(Role.ADMIN, "USER_REACTIVATED", true, admin.userId(), admin.sessionId(), ci, "user " + userId + "; reason: " + reason.trim());
    }

    private void requireUser(AuthPrincipal admin, UUID userId) {
        if (admin.userId().equals(userId)) throw ApiException.forbidden("NOT_ALLOWED", "You can't change your own account here.");
        if (jdbc.queryForObject("select count(*) from users where id = ? and role = 'USER'", Integer.class, userId) == 0) throw ApiException.notFound("User not found.");   // admins can't be managed through this path
    }

    // ------------------------------------------------------------------ payment monitoring

    public PaymentPage payments(String status, String q, LocalDate from, LocalDate to, BigDecimal minAmount, boolean suspiciousOnly, int page, int size) {
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        String flagsSql = "(p.amount >= 10000) as high_amount, (select count(*) from payments x where x.payer_id = p.payer_id and x.created_at between p.created_at - interval '1 hour' and p.created_at) >= 10 as velocity, "
                + "(select count(*) from payments x where x.payer_id = p.payer_id and x.status = 'FAILED' and x.created_at between p.created_at - interval '24 hours' and p.created_at) >= 3 as repeat_fail";
        StringBuilder w = new StringBuilder(" where 1 = 1"); List<Object> a = new ArrayList<>();
        if (status != null && status.matches("PENDING|SUCCESSFUL|FAILED|CANCELLED|REFUNDED")) { w.append(" and p.status = ?"); a.add(status); }
        if (q != null && !q.isBlank()) { w.append(" and p.reference = ?"); a.add(q.trim().toUpperCase()); }
        if (from != null) { w.append(" and p.created_at >= ?"); a.add(java.sql.Date.valueOf(from)); }
        if (to != null) { w.append(" and p.created_at < ?"); a.add(java.sql.Date.valueOf(to.plusDays(1))); }
        if (minAmount != null) { w.append(" and p.amount >= ?"); a.add(minAmount); }
        String base = "from payments p join user_profiles pr on pr.user_id = p.payer_id join user_profiles pe on pe.user_id = p.payee_id" + w;
        String flagged = "select * from (select p.*, pr.full_name as payer_name, pe.full_name as payee_name, " + flagsSql + ", (select d.status from payment_disputes d where d.payment_id = p.id order by d.created_at desc limit 1) as dispute_status " + base + ") z" + (suspiciousOnly ? " where z.high_amount or z.velocity or z.repeat_fail" : "");
        Long total = jdbc.queryForObject("select count(*) from (" + flagged + ") c", Long.class, a.toArray());
        List<Object> pa = new ArrayList<>(a); pa.add(sz); pa.add((long) pg * sz);
        List<PaymentRow> rows = jdbc.query(flagged + " order by created_at desc limit ? offset ?", (rs, i) -> {
            List<String> flags = new ArrayList<>();
            if (rs.getBoolean("high_amount")) flags.add("High amount"); if (rs.getBoolean("velocity")) flags.add("10+ payments in an hour by payer"); if (rs.getBoolean("repeat_fail")) flags.add("3+ failures in 24h by payer");
            return new PaymentRow(rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("payer_name"), rs.getString("payee_name"), rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), rs.getString("status"), rs.getString("failure_reason"), rs.getTimestamp("created_at").toInstant(), flags, rs.getString("dispute_status"));
        }, pa.toArray());
        return new PaymentPage(rows, pg, sz, total == null ? 0 : total);
    }

    public Map<String, Object> paymentDetail(AuthPrincipal admin, UUID id, ClientInfo ci) {
        PaymentRow row = jdbc.query("select p.*, pr.full_name as payer_name, pe.full_name as payee_name, false as high_amount, false as velocity, false as repeat_fail, (select d.status from payment_disputes d where d.payment_id = p.id order by d.created_at desc limit 1) as dispute_status "
                + "from payments p join user_profiles pr on pr.user_id = p.payer_id join user_profiles pe on pe.user_id = p.payee_id where p.id = ?",
                (rs, i) -> new PaymentRow(rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("payer_name"), rs.getString("payee_name"), rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), rs.getString("status"), rs.getString("failure_reason"), rs.getTimestamp("created_at").toInstant(), List.of(), rs.getString("dispute_status")), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Payment not found."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("payment", row);
        out.put("events", jdbc.query("select e.from_status, e.to_status, e.note, e.created_at, coalesce(up.full_name, 'System') from payment_events e left join user_profiles up on up.user_id = e.actor_user_id where e.payment_id = ? order by e.created_at", (rs, i) -> Map.of("from", String.valueOf(rs.getString(1)), "to", rs.getString(2), "note", String.valueOf(rs.getString(3)), "at", rs.getTimestamp(4).toInstant().toString(), "actor", rs.getString(5)), id));
        out.put("disputes", jdbc.query("select status, reason, resolution_note, created_at, resolved_at from payment_disputes where payment_id = ? order by created_at", (rs, i) -> Map.of("status", rs.getString(1), "reason", rs.getString(2), "resolution", String.valueOf(rs.getString(3)), "opened", rs.getTimestamp(4).toInstant().toString()), id));
        out.put("refund", jdbc.query("select amount, reason, created_at from payment_refunds where payment_id = ?", (rs, i) -> Map.of("amount", rs.getBigDecimal(1).toPlainString(), "reason", String.valueOf(rs.getString(2)), "at", rs.getTimestamp(3).toInstant().toString()), id).stream().findFirst().orElse(null));
        out.put("relatedTickets", jdbc.query("select 'FP-T-' || ticket_no, status from support_tickets where payment_id = ?", (rs, i) -> Map.of("ticket", rs.getString(1), "status", rs.getString(2)), id));
        audit.record(Role.ADMIN, "PAYMENT_VIEWED", true, admin.userId(), admin.sessionId(), ci, "payment " + id);
        return out;
    }

    @Transactional
    public void refundPayment(AuthPrincipal admin, UUID id, String reason, ClientInfo ci) {
        payments.adminRefund(admin.userId(), id, reason);
        audit.record(Role.ADMIN, "PAYMENT_ADMIN_REFUND", true, admin.userId(), admin.sessionId(), ci, "payment " + id + "; reason: " + reason.trim());
    }

    @Transactional
    public void rejectDispute(AuthPrincipal admin, UUID id, String note, ClientInfo ci) {
        payments.adminRejectDispute(admin.userId(), id, note);
        audit.record(Role.ADMIN, "PAYMENT_DISPUTE_REJECTED", true, admin.userId(), admin.sessionId(), ci, "payment " + id + "; " + note.trim());
    }

    // ------------------------------------------------------------------ audit & timeline

    public AuditPage auditLog(AuthPrincipal admin, String scope, String type, String actor, String outcome, LocalDate from, LocalDate to, int page, int size, boolean timeline, ClientInfo ci) {
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        StringBuilder w = new StringBuilder(" where 1 = 1"); List<Object> a = new ArrayList<>();
        if (timeline) w.append(" and (e.scope = 'ADMIN' or e.event_type like 'PAYMENT%' or e.event_type like 'TICKET%' or e.event_type in ('ACCOUNT_REGISTERED','ACCOUNT_DELETED'))");
        if (scope != null && scope.matches("USER|ADMIN")) { w.append(" and e.scope = ?"); a.add(scope); }
        if (type != null && type.matches("[A-Z_]{3,64}")) { w.append(" and e.event_type = ?"); a.add(type); }
        if (outcome != null && outcome.matches("SUCCESS|FAILURE")) { w.append(" and e.outcome = ?"); a.add(outcome); }
        if (actor != null && !actor.isBlank()) { w.append(" and lower(u.email) = ?"); a.add(actor.trim().toLowerCase()); }
        if (from != null) { w.append(" and e.created_at >= ?"); a.add(java.sql.Date.valueOf(from)); }
        if (to != null) { w.append(" and e.created_at < ?"); a.add(java.sql.Date.valueOf(to.plusDays(1))); }
        Long total = jdbc.queryForObject("select count(*) from audit_events e left join users u on u.id = e.actor_user_id" + w, Long.class, a.toArray());
        List<Object> pa = new ArrayList<>(a); pa.add(sz); pa.add((long) pg * sz);
        List<AuditItem> items = jdbc.query(AUDIT_SELECT + w + " order by e.created_at desc limit ? offset ?", (rs, i) -> item(rs), pa.toArray());
        if (!timeline) audit.record(Role.ADMIN, "AUDIT_LOG_VIEWED", true, admin.userId(), admin.sessionId(), ci, "page " + pg);   // privileged access is itself recorded
        return new AuditPage(items, pg, sz, total == null ? 0 : total);
    }

    private static final String AUDIT_SELECT = "select e.id, e.scope, e.event_type, e.outcome, u.email, e.actor_role, e.ip_address, e.details, e.created_at from audit_events e left join users u on u.id = e.actor_user_id";

    private List<AuditItem> recent(int n) {
        return jdbc.query(AUDIT_SELECT + " order by e.created_at desc limit ?", (rs, i) -> item(rs), n);
    }

    private static AuditItem item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant());
    }

    // ------------------------------------------------------------------ oversight: default categories & investigations

    /** Cached for 5 minutes (caffeine, per instance). Non-authoritative: PostgreSQL remains the source of truth; writes evict. */
    @Cacheable("defaultCategories")
    public List<DefaultCategory> defaultCategories() {
        return jdbc.query("select c.id, c.name, c.kind, c.color, c.archived_at, (select count(*) from transactions t where t.category_id = c.id) as usage from categories c where c.user_id is null order by c.kind, c.sort_order, c.name",
                (rs, i) -> new DefaultCategory(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5) != null, rs.getLong(6)));
    }

    @CacheEvict(value = "defaultCategories", allEntries = true)
    @Transactional
    public DefaultCategory createDefaultCategory(AuthPrincipal admin, CategoryRequest r, ClientInfo ci) {
        UUID id = jdbc.queryForObject("insert into categories (user_id, name, kind, color, sort_order) values (null, ?, ?, ?, coalesce((select max(sort_order) + 10 from categories where user_id is null and kind = ?), 100)) returning id", UUID.class, r.name().trim(), r.kind(), r.color(), r.kind());
        audit.record(Role.ADMIN, "DEFAULT_CATEGORY_CREATED", true, admin.userId(), admin.sessionId(), ci, r.name().trim());
        return defaultCategories().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @CacheEvict(value = "defaultCategories", allEntries = true)
    @Transactional
    public DefaultCategory renameDefaultCategory(AuthPrincipal admin, UUID id, CategoryRequest r, ClientInfo ci) {
        if (jdbc.update("update categories set name = ?, color = ? where id = ? and user_id is null and kind = ?", r.name().trim(), r.color(), id, r.kind()) == 0) throw ApiException.notFound("Default category not found (the kind cannot be changed).");
        audit.record(Role.ADMIN, "DEFAULT_CATEGORY_UPDATED", true, admin.userId(), admin.sessionId(), ci, id + " -> " + r.name().trim());
        return defaultCategories().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @CacheEvict(value = "defaultCategories", allEntries = true)
    @Transactional
    public void archiveDefaultCategory(AuthPrincipal admin, UUID id, boolean archived, ClientInfo ci) {
        if (jdbc.update("update categories set archived_at = " + (archived ? "now()" : "null") + " where id = ? and user_id is null", id) == 0) throw ApiException.notFound("Default category not found.");
        audit.record(Role.ADMIN, archived ? "DEFAULT_CATEGORY_ARCHIVED" : "DEFAULT_CATEGORY_RESTORED", true, admin.userId(), admin.sessionId(), ci, id.toString());
    }

    @Transactional
    public UUID openInvestigation(AuthPrincipal admin, InvestigationRequest r, ClientInfo ci) {
        if (jdbc.queryForObject("select count(*) from users where id = ? and role = 'USER'", Integer.class, r.userId()) == 0) throw ApiException.notFound("User not found.");
        if (r.ticketId() != null && jdbc.queryForObject("select count(*) from support_tickets where id = ? and user_id = ?", Integer.class, r.ticketId(), r.userId()) == 0) throw ApiException.badRequest("TICKET_MISMATCH", "That ticket does not belong to this user.");
        UUID id = jdbc.queryForObject("insert into admin_investigations (admin_id, user_id, ticket_id, reason, expires_at) values (?, ?, ?, ?, ?) returning id", UUID.class, admin.userId(), r.userId(), r.ticketId(), r.reason().trim(), java.sql.Timestamp.from(Instant.now().plus(Duration.ofHours(24))));
        audit.record(Role.ADMIN, "INVESTIGATION_OPENED", true, admin.userId(), admin.sessionId(), ci, "user " + r.userId() + (r.ticketId() == null ? "" : " ticket " + r.ticketId()) + "; reason: " + r.reason().trim());
        return id;
    }

    /** Aggregates only: counts, balances and payment references. No merchants, notes, receipts or memos. */
    public InvestigationSummary investigation(AuthPrincipal admin, UUID id, ClientInfo ci) {
        var rows = jdbc.queryForList("select user_id, expires_at from admin_investigations where id = ? and admin_id = ?", id, admin.userId());
        if (rows.isEmpty()) throw ApiException.notFound("Investigation not found.");
        UUID user = (UUID) rows.get(0).get("user_id"); Instant exp = ((java.sql.Timestamp) rows.get(0).get("expires_at")).toInstant();
        if (exp.isBefore(Instant.now())) throw new ApiException(HttpStatus.GONE, "INVESTIGATION_EXPIRED", "This investigation window has expired. Open a new one with a fresh reason.");
        Map<String, String> balances = new TreeMap<>();
        jdbc.query("select currency, sum(current_balance) from accounts where user_id = ? and archived_at is null group by currency", rs -> { balances.put(rs.getString(1), rs.getBigDecimal(2).toPlainString()); }, user);
        var who = jdbc.query("select p.full_name, u.email, u.status from users u join user_profiles p on p.user_id = u.id where u.id = ?", (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, user).get(0);
        audit.record(Role.ADMIN, "INVESTIGATION_VIEWED", true, admin.userId(), admin.sessionId(), ci, "investigation " + id + " user " + user);
        return new InvestigationSummary(id, who[0], who[1], who[2], exp, n("select count(*) from accounts where user_id = '" + user + "' and archived_at is null"), balances,
                n("select count(*) from transactions where user_id = '" + user + "' and created_at > now() - interval '90 days'"), n("select count(*) from payments where (payer_id = '" + user + "' or payee_id = '" + user + "') and created_at > now() - interval '90 days'"),
                n("select count(*) from bills where user_id = '" + user + "' and status = 'ACTIVE' and archived_at is null"), n("select count(*) from budgets where user_id = '" + user + "' and archived_at is null"),
                jdbc.query("select reference, status, amount || ' ' || currency from payments where payer_id = ? or payee_id = ? order by created_at desc limit 10", (rs, i) -> Map.of("reference", rs.getString(1), "status", rs.getString(2), "amount", rs.getString(3)), user, user),
                List.of("Counts and balances only. Merchant names, notes, memos and receipts are intentionally not shown.", "Access expires " + exp + " and is recorded in the audit log."));
    }

    // ------------------------------------------------------------------ announcements

    public List<AnnouncementView> announcements() {
        return jdbc.query("select a.*, (select count(*) from notifications n where n.dedupe_key = 'announce:' || a.id and n.read_at is not null) as read_count from announcements a order by a.created_at desc limit 50",
                (rs, i) -> new AnnouncementView(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body"), rs.getString("audience"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(), rs.getInt("delivered"), rs.getLong("read_count"), emails(rs.getString("target_emails")).size()));
    }

    @Transactional
    public AnnouncementView saveAnnouncement(AuthPrincipal admin, UUID id, AnnouncementRequest r, ClientInfo ci) {
        List<String> emails = emails(r.targetEmails());
        if (r.audience().equals("SPECIFIC") && (emails.isEmpty() || emails.size() > 200)) throw ApiException.badRequest("INVALID_AUDIENCE", "List between 1 and 200 email addresses for a targeted announcement.");
        UUID out;
        if (id == null) {
            out = jdbc.queryForObject("insert into announcements (title, body, audience, target_emails, created_by) values (?, ?, ?, ?, ?) returning id", UUID.class, r.title().trim(), r.body().trim(), r.audience(), r.audience().equals("SPECIFIC") ? String.join(",", emails) : null, admin.userId());
        } else {
            if (jdbc.update("update announcements set title = ?, body = ?, audience = ?, target_emails = ? where id = ? and status = 'DRAFT'", r.title().trim(), r.body().trim(), r.audience(), r.audience().equals("SPECIFIC") ? String.join(",", emails) : null, id) == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_EDITABLE", "Published announcements can't be edited.");
            out = id;
        }
        audit.record(Role.ADMIN, id == null ? "ANNOUNCEMENT_DRAFTED" : "ANNOUNCEMENT_EDITED", true, admin.userId(), admin.sessionId(), ci, out.toString());
        return announcements().stream().filter(a -> a.id().equals(out)).findFirst().orElseThrow();
    }

    /** Delivery = one SYSTEM notification per recipient (idempotent via the dedupe key), so a repeat publish can never double-send. */
    @Transactional
    public AnnouncementView publish(AuthPrincipal admin, UUID id, ClientInfo ci) {
        var rows = jdbc.queryForList("select title, body, audience, target_emails, status from announcements where id = ? for update", id);
        if (rows.isEmpty()) throw ApiException.notFound("Announcement not found.");
        var a = rows.get(0);
        if (!"DRAFT".equals(a.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PUBLISHED", "This announcement was already published.");
        String key = "announce:" + id; String base = "insert into notifications (user_id, type, title, body, link, dedupe_key) select u.id, 'SYSTEM', ?, ?, null, ? from users u where u.role = 'USER' and u.status = 'ACTIVE'";
        int delivered = switch ((String) a.get("audience")) {
            case "ALL" -> jdbc.update(base + " on conflict (user_id, dedupe_key) do nothing", a.get("title"), a.get("body"), key);
            case "NEW_USERS_30D" -> jdbc.update(base + " and u.created_at > now() - interval '30 days' on conflict (user_id, dedupe_key) do nothing", a.get("title"), a.get("body"), key);
            default -> jdbc.update(base + " and u.email = any (string_to_array(?, ',')) on conflict (user_id, dedupe_key) do nothing", a.get("title"), a.get("body"), key, a.get("target_emails"));
        };
        jdbc.update("update announcements set status = 'PUBLISHED', published_at = now(), delivered = ? where id = ?", delivered, id);
        audit.record(Role.ADMIN, "ANNOUNCEMENT_PUBLISHED", true, admin.userId(), admin.sessionId(), ci, id + " delivered " + delivered);
        live.pushEveryone("notifications");
        return announcements().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteDraft(AuthPrincipal admin, UUID id, ClientInfo ci) {
        if (jdbc.update("delete from announcements where id = ? and status = 'DRAFT'", id) == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_DELETABLE", "Only drafts can be deleted; published announcements stay in the history.");
        audit.record(Role.ADMIN, "ANNOUNCEMENT_DRAFT_DELETED", true, admin.userId(), admin.sessionId(), ci, id.toString());
    }

    // ------------------------------------------------------------------ analytics (aggregates only, no personal records)

    public static final List<String> REPORTS = List.of("user-growth", "platform-usage", "transaction-volumes", "payment-outcomes", "support-workload", "operational-health");

    public ReportTable report(String type, LocalDate from, LocalDate to) {
        LocalDate t = to == null ? LocalDate.now() : to, f = from == null ? t.minusDays(29) : from;
        if (t.isBefore(f) || java.time.temporal.ChronoUnit.DAYS.between(f, t) > 366) throw ApiException.badRequest("INVALID_RANGE", "Choose a range of at most one year.");
        return switch (type) {
            case "user-growth" -> table(type, "User growth", f, t, List.of("Day", "New users", "Cumulative users"), "select d::date, (select count(*) from users u where u.role = 'USER' and u.created_at::date = d::date), (select count(*) from users u where u.role = 'USER' and u.created_at::date <= d::date) from generate_series(?::date, ?::date, interval '1 day') d order by d", f, t);
            case "platform-usage" -> table(type, "Platform usage", f, t, List.of("Day", "Active users (signed in)", "Transactions recorded"), "select d::date, (select count(distinct s.user_id) from auth_sessions s where s.created_at::date = d::date), (select count(*) from transactions x where x.created_at::date = d::date and x.type in ('INCOME','EXPENSE','TRANSFER') and x.reverses_transaction_id is null) from generate_series(?::date, ?::date, interval '1 day') d order by d", f, t);
            case "transaction-volumes" -> table(type, "Transaction volumes", f, t, List.of("Type", "Count", "Total (all currencies, nominal)"), "select t.type, count(*), coalesce(sum(abs(e.amount)), 0) from transactions t join ledger_entries e on e.transaction_id = t.id where t.occurred_on between ? and ? and t.reverses_transaction_id is null and t.type <> 'OPENING_BALANCE' group by t.type order by t.type", f, t);
            case "payment-outcomes" -> table(type, "Simulated payment outcomes", f, t, List.of("Status", "Payments", "Amount (nominal)"), "select status, count(*), coalesce(sum(amount), 0) from payments where created_at::date between ? and ? group by status order by status", f, t);
            case "support-workload" -> table(type, "Support workload", f, t, List.of("Category", "Tickets opened", "Still open"), "select category, count(*), count(*) filter (where status in ('OPEN','IN_PROGRESS','AWAITING_USER')) from support_tickets where created_at::date between ? and ? group by category order by category", f, t);
            case "operational-health" -> {
                Health h = health();
                yield new ReportTable(type, "Operational health snapshot", "n/a", null, null, List.of("Measure", "Value"), List.of(List.of("Database latency (ms)", String.valueOf(h.dbLatencyMs())), List.of("Live WebSocket connections", String.valueOf(h.liveConnections())),
                        List.of("Heap used / max (MB)", h.heapUsedMb() + " / " + h.heapMaxMb()), List.of("Uptime (minutes)", String.valueOf(h.uptimeMinutes())), List.of("Latest schema version", h.schemaVersion()), List.of("Stale background work", String.valueOf(h.staleBackgroundWork()))), h.checks());
            }
            default -> throw ApiException.notFound("Unknown report.");
        };
    }

    private ReportTable table(String type, String title, LocalDate f, LocalDate t, List<String> cols, String sql, Object... args) {
        List<List<String>> rows = jdbc.query(sql, (rs, i) -> { List<String> r = new ArrayList<>(); for (int c = 1; c <= cols.size(); c++) { Object v = rs.getObject(c); r.add(v instanceof BigDecimal b ? b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : String.valueOf(v)); } return r; }, args);
        return new ReportTable(type, title, "mixed", f, t, cols, rows, List.of("Aggregated, privacy-aware figures. No individual user records are included."));
    }

    // ------------------------------------------------------------------ helpers

    private long n(String sql) { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0 : v; }

    private Map<String, Long> counts(String sql) {
        Map<String, Long> m = new TreeMap<>();
        jdbc.query(sql, rs -> { m.put(rs.getString(1), rs.getLong(2)); });
        return m;
    }

    private static List<String> emails(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("[,;\\s]+")).map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> s.matches("^[^@\\s]+@[^@\\s]+$")).distinct().toList();
    }
}