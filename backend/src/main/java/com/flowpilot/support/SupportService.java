package com.flowpilot.support;

import com.flowpilot.admin.SettingsService;
import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.domain.Role;
import com.flowpilot.files.FileStorageService;
import com.flowpilot.files.FileValidator;
import com.flowpilot.files.ReceiptService;
import com.flowpilot.notify.LiveUpdateRegistry;
import com.flowpilot.notify.NotificationService;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.support.SupportDtos.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tickets and chat. PostgreSQL is the source of truth: every message and event is persisted first; the WebSocket only sends
 * a hint so connected parties re-read. Users can only ever see their own tickets; ownership is checked in SQL on every call.
 */
@Service
public class SupportService {
    private final JdbcTemplate jdbc;
    private final FileStorageService files;
    private final FileValidator validator;
    private final NotificationService notifications;
    private final LiveUpdateRegistry live;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final SettingsService settings;

    public SupportService(JdbcTemplate jdbc, FileStorageService files, FileValidator validator, NotificationService notifications, LiveUpdateRegistry live, AuditService audit, RateLimiter limiter, SettingsService settings) {
        this.jdbc = jdbc; this.files = files; this.validator = validator; this.notifications = notifications; this.live = live; this.audit = audit; this.limiter = limiter; this.settings = settings;
    }

    // ------------------------------------------------------------------ create / list

    @Transactional
    public TicketView create(UUID userId, CreateTicket r) {
        limiter.check("ticket-create", userId.toString(), 5, Duration.ofHours(1));
        if (r.paymentId() != null && jdbc.queryForObject("select count(*) from payments where id = ? and (payer_id = ? or payee_id = ?)", Integer.class, r.paymentId(), userId, userId) == 0) throw ApiException.notFound("Payment not found.");
        UUID id = jdbc.queryForObject("insert into support_tickets (user_id, title, category, priority, description, payment_id) values (?, ?, ?, ?, ?, ?) returning id", UUID.class, userId, r.title().trim(), r.category(), r.priority(), r.description().trim(), r.paymentId());
        event(id, userId, "USER", "CREATED", null, "OPEN", null);
        String no = jdbc.queryForObject("select 'FP-T-' || ticket_no from support_tickets where id = ?", String.class, id);
        afterCommit(() -> {
            for (UUID admin : jdbc.queryForList("select id from users where role = 'ADMIN' and status = 'ACTIVE'", UUID.class)) notifications.notify(admin, "SUPPORT", "New ticket " + no, r.title().trim() + " (" + r.priority().toLowerCase() + ")", "/admin/support", "ticket-new:" + id + ":" + admin);
            live.pushAdmins("support");
            audit.record(Role.USER, "TICKET_CREATED", true, userId, null, null, no);
        });
        return one(userId, Role.USER, id);
    }

    public PageOf<TicketView> mine(UUID userId, String status, int page, int size) {
        return list(userId, Role.USER, status, null, null, null, false, page, size);
    }

    /** Admin queue. Filters are all optional and parameterised. */
    public PageOf<TicketView> queue(UUID adminId, String status, String priority, String category, String q, boolean mineOnly, int page, int size) {
        return list(adminId, Role.ADMIN, status, priority, category, q, mineOnly, page, size);
    }

    private PageOf<TicketView> list(UUID me, Role role, String status, String priority, String category, String q, boolean mineOnly, int page, int size) {
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        StringBuilder where = new StringBuilder(role == Role.USER ? " where t.user_id = ?" : " where 1 = 1");
        List<Object> args = new ArrayList<>(); if (role == Role.USER) args.add(me);
        if (status != null && status.matches("OPEN|IN_PROGRESS|AWAITING_USER|RESOLVED|CLOSED")) { where.append(" and t.status = ?"); args.add(status); }
        if (priority != null && priority.matches("LOW|MEDIUM|HIGH|URGENT")) { where.append(" and t.priority = ?"); args.add(priority); }
        if (category != null && category.matches("PAYMENT|RECORD|BUG|ACCOUNT|OTHER")) { where.append(" and t.category = ?"); args.add(category); }
        if (mineOnly && role == Role.ADMIN) { where.append(" and t.assigned_admin_id = ?"); args.add(me); }
        if (q != null && !q.isBlank()) { String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; where.append(" and (lower(t.title) like ? escape '\\' or ('fp-t-' || t.ticket_no) like ? escape '\\')"); args.add(like); args.add(like); }
        Long total = jdbc.queryForObject("select count(*) from support_tickets t" + where, Long.class, args.toArray());
        // unread = messages from the OTHER party that this viewer has not read yet
        List<TicketView> items = jdbc.query(BASE + where + " order by case t.priority when 'URGENT' then 0 when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end, t.updated_at desc limit ? offset ?",
                (rs, i) -> view(rs, role), buildListArgs(role, args, sz, (long) pg * sz));
        return new PageOf<>(items, pg, sz, total == null ? 0 : total);
    }

    private Object[] buildListArgs(Role role, List<Object> filterArgs, int sz, long offset) {
        List<Object> all = new ArrayList<>(); all.add(role == Role.USER ? "ADMIN" : "USER"); all.addAll(filterArgs); all.add(sz); all.add(offset);
        return all.toArray();
    }

    public long unread(UUID me, Role role) {
        if (role == Role.USER) return nz(jdbc.queryForObject("select count(*) from support_messages m join support_tickets t on t.id = m.ticket_id where t.user_id = ? and m.sender_role = 'ADMIN' and m.read_at is null", Long.class, me));
        return nz(jdbc.queryForObject("select count(*) from support_tickets t where (t.status = 'OPEN' and t.assigned_admin_id is null) or exists (select 1 from support_messages m where m.ticket_id = t.id and m.sender_role = 'USER' and m.read_at is null)", Long.class));
    }

    // ------------------------------------------------------------------ detail & chat

    @Transactional
    public Detail detail(AuthPrincipal p, UUID ticketId) {
        authorise(p, ticketId);
        String otherRole = p.role() == Role.ADMIN ? "USER" : "ADMIN";
        int read = jdbc.update("update support_messages set read_at = now() where ticket_id = ? and sender_role = ? and read_at is null", ticketId, otherRole);
        if (read > 0) afterCommit(() -> pushOther(p, ticketId));
        List<MessageView> messages = jdbc.query("select m.*, up.full_name as sender_name, f.original_name, f.mime_type from support_messages m join user_profiles up on up.user_id = m.sender_id left join stored_files f on f.id = m.file_id where m.ticket_id = ? order by m.created_at",
                (rs, i) -> new MessageView(rs.getObject("id", UUID.class), rs.getString("sender_role"), rs.getString("sender_name"), rs.getString("body"), rs.getObject("file_id") == null ? null : rs.getObject("id", UUID.class), rs.getString("original_name"), rs.getString("mime_type"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(), rs.getString("sender_role").equals(p.role().name())), ticketId);
        List<EventView> events = jdbc.query("select e.*, up.full_name as actor_name from support_events e join user_profiles up on up.user_id = e.actor_id where e.ticket_id = ? order by e.created_at",
                (rs, i) -> new EventView(rs.getString("actor_role"), rs.getString("actor_name"), rs.getString("event_type"), rs.getString("from_value"), rs.getString("to_value"), rs.getString("note"), rs.getTimestamp("created_at").toInstant()), ticketId);
        return new Detail(one(p.userId(), p.role(), ticketId), messages, events);
    }

    @Transactional
    public Detail post(AuthPrincipal p, UUID ticketId, String body, MultipartFile file, ClientInfo ci) {
        limiter.check("ticket-msg", p.userId().toString(), 30, Duration.ofMinutes(1));
        authorise(p, ticketId);
        String status = jdbc.queryForObject("select status from support_tickets where id = ? for update", String.class, ticketId);
        if (status.equals("CLOSED")) throw new ApiException(HttpStatus.CONFLICT, "TICKET_CLOSED", "This ticket is closed.");
        String text = body == null || body.isBlank() ? null : body.trim();
        if (text != null && text.length() > 4000) throw ApiException.badRequest("MESSAGE_TOO_LONG", "Messages can be at most 4,000 characters.");
        UUID fileId = null;
        if (file != null && !file.isEmpty()) {
            var v = validator.validate(file, ReceiptService.ALLOWED, ReceiptService.MAX_BYTES);
            fileId = files.storePrivate(p.userId(), "COMPLAINT_EVIDENCE", v).id();
        }
        if (text == null && fileId == null) throw ApiException.badRequest("EMPTY_MESSAGE", "Write a message or attach a file.");
        jdbc.update("insert into support_messages (ticket_id, sender_id, sender_role, body, file_id) values (?, ?, ?, ?, ?)", ticketId, p.userId(), p.role().name(), text, fileId);
        if (p.role() == Role.ADMIN) {
            jdbc.update("update support_tickets set first_admin_reply_at = coalesce(first_admin_reply_at, now()), assigned_admin_id = coalesce(assigned_admin_id, ?), updated_at = now(), status = case when status = 'OPEN' then 'IN_PROGRESS' else status end where id = ?", p.userId(), ticketId);
            if (status.equals("OPEN")) event(ticketId, p.userId(), "ADMIN", "STATUS", "OPEN", "IN_PROGRESS", "Replied");
            audit.record(Role.ADMIN, "TICKET_REPLY", true, p.userId(), p.sessionId(), ci, "ticket " + ticketId);
        } else {
            jdbc.update("update support_tickets set updated_at = now(), status = case when status = 'AWAITING_USER' then 'IN_PROGRESS' else status end where id = ?", ticketId);
            if (status.equals("AWAITING_USER")) event(ticketId, p.userId(), "USER", "STATUS", "AWAITING_USER", "IN_PROGRESS", "User replied");
        }
        afterCommit(() -> {
            if (p.role() == Role.ADMIN) { UUID owner = ownerOf(ticketId); notifications.notify(owner, "SUPPORT", "Support replied to your ticket", text == null ? "An attachment was shared." : trunc(text, 120), "/app/support", "reply:" + UUID.randomUUID()); live.push(owner, "support"); }
            else { live.pushAdmins("support"); }
        });
        return detail(p, ticketId);
    }

    public FileStorageService.Content attachment(AuthPrincipal p, UUID messageId) {
        var rows = jdbc.queryForList("select m.file_id, m.ticket_id from support_messages m where m.id = ? and m.file_id is not null", messageId);
        if (rows.isEmpty()) throw ApiException.notFound("Attachment not found.");
        authorise(p, (UUID) rows.get(0).get("ticket_id"));
        return files.readAuthorised((UUID) rows.get(0).get("file_id"));
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public TicketView reopen(UUID userId, UUID ticketId) {
        var rows = jdbc.queryForList("select status, resolved_at from support_tickets where id = ? and user_id = ? for update", ticketId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Ticket not found.");
        Instant resolved = rows.get(0).get("resolved_at") == null ? null : ((java.sql.Timestamp) rows.get(0).get("resolved_at")).toInstant();
        int days = settings.integer("support.reopen_days", 14);
        if (!"RESOLVED".equals(rows.get(0).get("status")) || resolved == null || resolved.isBefore(Instant.now().minus(Duration.ofDays(days)))) throw new ApiException(HttpStatus.CONFLICT, "NOT_REOPENABLE", "Only tickets resolved within the last " + days + " days can be reopened. Please open a new ticket.");
        jdbc.update("update support_tickets set status = 'OPEN', resolved_at = null, updated_at = now() where id = ?", ticketId);
        event(ticketId, userId, "USER", "STATUS", "RESOLVED", "OPEN", "Reopened by user");
        afterCommit(() -> live.pushAdmins("support"));
        return one(userId, Role.USER, ticketId);
    }

    @Transactional
    public TicketView userClose(UUID userId, UUID ticketId) {
        var cur = jdbc.query("select status from support_tickets where id = ? and user_id = ? for update", (rs, i) -> rs.getString(1), ticketId, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Ticket not found."));
        if (cur.equals("CLOSED")) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLOSED", "This ticket is already closed.");
        jdbc.update("update support_tickets set status = 'CLOSED', closed_at = now(), updated_at = now() where id = ?", ticketId);
        event(ticketId, userId, "USER", "STATUS", cur, "CLOSED", "Closed by user");
        afterCommit(() -> live.pushAdmins("support"));
        return one(userId, Role.USER, ticketId);
    }

    @Transactional
    public TicketView setStatus(AuthPrincipal admin, UUID ticketId, String status, String note, ClientInfo ci) {
        var cur = jdbc.query("select status, user_id from support_tickets where id = ? for update", (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2, UUID.class)}, ticketId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Ticket not found."));
        String from = (String) cur[0];
        if (from.equals(status)) throw new ApiException(HttpStatus.CONFLICT, "NO_CHANGE", "The ticket already has that status.");
        jdbc.update("update support_tickets set status = ?, updated_at = now(), assigned_admin_id = coalesce(assigned_admin_id, ?), resolved_at = case when ? = 'RESOLVED' then now() else resolved_at end, closed_at = case when ? = 'CLOSED' then now() else closed_at end where id = ?", status, admin.userId(), status, status, ticketId);
        event(ticketId, admin.userId(), "ADMIN", "STATUS", from, status, note);
        audit.record(Role.ADMIN, "TICKET_STATUS_CHANGED", true, admin.userId(), admin.sessionId(), ci, "ticket " + ticketId + ": " + from + " -> " + status);
        UUID owner = (UUID) cur[1];
        afterCommit(() -> { notifications.notify(owner, "SUPPORT", "Your ticket is now " + status.toLowerCase().replace('_', ' '), note == null ? null : trunc(note, 200), "/app/support", "status:" + ticketId + ":" + status + ":" + System.currentTimeMillis()); live.push(owner, "support"); live.pushAdmins("support"); });
        return one(admin.userId(), Role.ADMIN, ticketId);
    }

    @Transactional
    public TicketView setPriority(AuthPrincipal admin, UUID ticketId, String priority, ClientInfo ci) {
        String from = jdbc.query("select priority from support_tickets where id = ? for update", (rs, i) -> rs.getString(1), ticketId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Ticket not found."));
        jdbc.update("update support_tickets set priority = ?, updated_at = now() where id = ?", priority, ticketId);
        event(ticketId, admin.userId(), "ADMIN", "PRIORITY", from, priority, null);
        audit.record(Role.ADMIN, "TICKET_PRIORITY_CHANGED", true, admin.userId(), admin.sessionId(), ci, "ticket " + ticketId + ": " + from + " -> " + priority);
        return one(admin.userId(), Role.ADMIN, ticketId);
    }

    @Transactional
    public TicketView assignToMe(AuthPrincipal admin, UUID ticketId, ClientInfo ci) {
        if (jdbc.update("update support_tickets set assigned_admin_id = ?, updated_at = now() where id = ?", admin.userId(), ticketId) == 0) throw ApiException.notFound("Ticket not found.");
        event(ticketId, admin.userId(), "ADMIN", "ASSIGNED", null, admin.userId().toString(), null);
        audit.record(Role.ADMIN, "TICKET_ASSIGNED", true, admin.userId(), admin.sessionId(), ci, "ticket " + ticketId);
        return one(admin.userId(), Role.ADMIN, ticketId);
    }

    // ------------------------------------------------------------------ internals

    private static final String BASE = "select t.*, 'FP-T-' || t.ticket_no as no, rq.full_name as requester_name, u.email as requester_email, ad.full_name as admin_name, p.reference as pay_ref, "
            + "(select count(*) from support_messages m where m.ticket_id = t.id and m.sender_role = ? and m.read_at is null) as unread "
            + "from support_tickets t join user_profiles rq on rq.user_id = t.user_id join users u on u.id = t.user_id left join user_profiles ad on ad.user_id = t.assigned_admin_id left join payments p on p.id = t.payment_id";

    private TicketView one(UUID me, Role role, UUID id) {
        return jdbc.query(BASE + " where t.id = ?" + (role == Role.USER ? " and t.user_id = ?" : ""), (rs, i) -> view(rs, role), role == Role.USER ? new Object[]{"ADMIN", id, me} : new Object[]{"USER", id}).stream().findFirst().orElseThrow(() -> ApiException.notFound("Ticket not found."));
    }

    private TicketView view(java.sql.ResultSet rs, Role role) throws java.sql.SQLException {
        String status = rs.getString("status");
        java.sql.Timestamp resolved = rs.getTimestamp("resolved_at");
        boolean reopen = role == Role.USER && status.equals("RESOLVED") && resolved != null && resolved.toInstant().isAfter(Instant.now().minus(Duration.ofDays(settings.integer("support.reopen_days", 14))));
        return new TicketView(rs.getObject("id", UUID.class), rs.getString("no"), rs.getString("title"), rs.getString("category"), rs.getString("priority"), rs.getString("description"), status, rs.getString("pay_ref"),
                role == Role.ADMIN ? rs.getString("requester_name") : null, role == Role.ADMIN ? rs.getString("requester_email") : null, rs.getString("admin_name"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("unread"), reopen, role == Role.USER && !status.equals("CLOSED"), !status.equals("CLOSED"));
    }

    /** Ownership/role gate used by every ticket operation. A user asking for someone else's ticket gets a plain 404. */
    private void authorise(AuthPrincipal p, UUID ticketId) {
        boolean ok = p.role() == Role.ADMIN ? jdbc.queryForObject("select count(*) from support_tickets where id = ?", Integer.class, ticketId) > 0
                : jdbc.queryForObject("select count(*) from support_tickets where id = ? and user_id = ?", Integer.class, ticketId, p.userId()) > 0;
        if (!ok) throw ApiException.notFound("Ticket not found.");
    }

    private void pushOther(AuthPrincipal p, UUID ticketId) {
        if (p.role() == Role.ADMIN) live.push(ownerOf(ticketId), "support"); else live.pushAdmins("support");
    }

    private UUID ownerOf(UUID ticketId) { return jdbc.queryForObject("select user_id from support_tickets where id = ?", UUID.class, ticketId); }

    private void event(UUID ticketId, UUID actor, String role, String type, String from, String to, String note) {
        jdbc.update("insert into support_events (ticket_id, actor_id, actor_role, event_type, from_value, to_value, note) values (?, ?, ?, ?, ?, ?, ?)", ticketId, actor, role, type, from, to, note);
    }

    private static long nz(Long v) { return v == null ? 0 : v; }
    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    private static void afterCommit(Runnable r) {
        Runnable safe = () -> { try { r.run(); } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(SupportService.class).error("Post-commit action failed", e); } };
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { safe.run(); } });
        else safe.run();
    }
}
