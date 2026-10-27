package com.flowpilot.payments;

import com.flowpilot.admin.SettingsService;
import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.domain.Role;
import com.flowpilot.finance.Account;
import com.flowpilot.finance.AccountRepository;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.notify.LiveUpdateRegistry;
import com.flowpilot.notify.NotificationService;
import com.flowpilot.payments.PaymentDtos.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SIMULATED payments between FlowPilot users. Nothing here talks to a bank, card network or UPI.
 * Money moves only through the immutable ledger, inside one database transaction, with both accounts row-locked in a
 * canonical order. The payment row is the authoritative state; every transition is appended to payment_events.
 */
@Service
public class PaymentService {
    static final Duration REQUEST_TTL = Duration.ofDays(7);
    static final Duration DISPUTE_WINDOW = Duration.ofDays(30);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no 0/O/1/I confusion

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final AccountRepository accounts;
    private final NotificationService notifications;
    private final LiveUpdateRegistry live;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final TransactionTemplate tx;
    private final SettingsService settings;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(JdbcTemplate jdbc, LedgerService ledger, AccountRepository accounts, NotificationService notifications, LiveUpdateRegistry live,
                          AuditService audit, RateLimiter limiter, PlatformTransactionManager tm, SettingsService settings) {
        this.settings = settings;
        this.jdbc = jdbc; this.ledger = ledger; this.accounts = accounts; this.notifications = notifications; this.live = live;
        this.audit = audit; this.limiter = limiter; this.tx = new TransactionTemplate(tm);
    }

    // ------------------------------------------------------------------ profile & lookup

    @Transactional
    public PaymentProfile profile(UUID userId) {
        List<PaymentProfile> p = jdbc.query("select handle, receive_account_id from payment_profiles where user_id = ?",
                (rs, i) -> new PaymentProfile(rs.getString(1), rs.getObject(2, UUID.class), true), userId);
        if (!p.isEmpty()) return p.get(0);
        String name = jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, userId);
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        slug = slug.length() < 3 ? "user" : slug.substring(0, Math.min(slug.length(), 20)).replaceAll("-+$", "");
        for (int i = 0; i < 8; i++) {
            String handle = slug + "-" + randomChars(4).toLowerCase(Locale.ROOT);
            if (jdbc.update("insert into payment_profiles (user_id, handle) values (?, ?) on conflict (handle) do nothing", userId, handle) == 1) return new PaymentProfile(handle, null, true);
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "HANDLE_UNAVAILABLE", "Could not allocate a payment handle. Please try again.");
    }

    @Transactional
    public PaymentProfile setReceiveAccount(UUID userId, UUID accountId) {
        PaymentProfile cur = profile(userId);
        if (accountId != null && jdbc.queryForObject("select count(*) from accounts where id = ? and user_id = ? and archived_at is null", Integer.class, accountId, userId) == 0) throw ApiException.notFound("Account not found.");
        jdbc.update("update payment_profiles set receive_account_id = ? where user_id = ?", accountId, userId);
        return new PaymentProfile(cur.handle(), accountId, true);
    }

    public Lookup lookup(UUID userId, String handle) {
        limiter.check("pay-lookup", userId.toString(), 30, Duration.ofMinutes(1));
        return jdbc.query("select ph.handle, up.full_name from payment_profiles ph join user_profiles up on up.user_id = ph.user_id join users u on u.id = ph.user_id where ph.handle = ? and u.status = 'ACTIVE' and u.role = 'USER' and ph.user_id <> ?",
                (rs, i) -> new Lookup(rs.getString(1), rs.getString(2)), handle, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("No FlowPilot user has that handle."));
    }

    // ------------------------------------------------------------------ requests

    @Transactional
    public RequestView createRequest(UUID userId, RequestCreate r) {
        limiter.check("pay-request", userId.toString(), 20, Duration.ofMinutes(1));
        if (r.amount().compareTo(maxAmount()) > 0) throw ApiException.badRequest("AMOUNT_TOO_LARGE", "Simulated payments are limited to " + maxAmount().toPlainString() + ".");
        profile(userId);
        UUID target = null;
        if (r.fromHandle() != null) target = userByHandle(r.fromHandle()).filter(u -> !u.equals(userId)).orElseThrow(() -> ApiException.notFound("No FlowPilot user has that handle."));
        for (int i = 0; i < 5; i++) {
            String ref = reference();
            try {
                UUID id = jdbc.queryForObject("insert into payment_requests (reference, requester_id, target_user_id, amount, currency, note, expires_at) values (?, ?, ?, ?, ?, ?, ?) returning id",
                        UUID.class, ref, userId, target, r.amount(), r.currency(), blank(r.note()), java.sql.Timestamp.from(Instant.now().plus(REQUEST_TTL)));
                if (target != null) { UUID t = target; String name = nameOf(userId); afterCommit(() -> notifyUser(t, "Payment request from " + name, r.amount().toPlainString() + " " + r.currency() + " (simulated)", "preq:" + id)); }
                return requestView(id, userId);
            } catch (DataIntegrityViolationException e) { /* reference collision: retry */ }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFERENCE_UNAVAILABLE", "Please try again.");
    }

    public List<RequestView> requests(UUID userId) {
        return jdbc.query(REQ_SQL + " where (r.requester_id = ? or r.target_user_id = ?) order by r.created_at desc limit 100", (rs, i) -> reqView(rs, userId), userId, userId, userId);
    }

    /** Anyone signed in with the reference (e.g. from a QR code) may view the request they are being asked to pay. */
    public RequestView requestByReference(UUID userId, String ref) {
        limiter.check("pay-reqlookup", userId.toString(), 30, Duration.ofMinutes(1));
        return jdbc.query(REQ_SQL + " where r.reference = ?", (rs, i) -> reqView(rs, userId), userId, ref).stream().findFirst().orElseThrow(() -> ApiException.notFound("Payment request not found."));
    }

    @Transactional
    public void cancelRequest(UUID userId, UUID id) {
        if (jdbc.update("update payment_requests set status = 'CANCELLED' where id = ? and requester_id = ? and status = 'OPEN'", id, userId) == 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE", "Only open requests you created can be cancelled.");
    }

    public String qr(UUID userId, String ref, String baseUrl) {
        UUID requester = jdbc.query("select requester_id from payment_requests where reference = ?", (rs, i) -> rs.getObject(1, UUID.class), ref).stream().findFirst().orElseThrow(() -> ApiException.notFound("Payment request not found."));
        if (!requester.equals(userId)) throw ApiException.notFound("Payment request not found.");
        return Qr.svg((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/app/payments?request=" + ref);
    }

    // ------------------------------------------------------------------ payments

    @Transactional
    public PaymentView initiate(UUID userId, InitiateRequest r, String idempotencyKey) {
        limiter.check("pay-initiate", userId.toString(), 30, Duration.ofMinutes(1));
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 80) throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is too long.");
        String hash = Integer.toHexString(Objects.hash(r.toHandle(), r.requestReference(), r.fromAccountId(), r.amount() == null ? null : r.amount().stripTrailingZeros().toPlainString(), r.note()));
        if (key != null) {
            var found = jdbc.queryForList("select request_hash, resource_id from idempotency_keys where user_id = ? and key = ? and operation = 'INITIATE_PAYMENT'", userId, key);
            if (!found.isEmpty()) {
                if (!hash.equals(found.get(0).get("request_hash"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", "That idempotency key was used with a different request.");
                return get(userId, (UUID) found.get(0).get("resource_id")).payment();
            }
        }
        if ((r.toHandle() == null) == (r.requestReference() == null)) throw ApiException.badRequest("INVALID_TARGET", "Choose a recipient handle or a payment request, not both.");

        UUID payee; BigDecimal amount; String currency; UUID requestId = null; String note = blank(r.note());
        if (r.requestReference() != null) {
            var rows = jdbc.queryForList("select id, requester_id, target_user_id, amount, currency, status, expires_at from payment_requests where reference = ? for update", r.requestReference());
            if (rows.isEmpty()) throw ApiException.notFound("Payment request not found.");
            var q = rows.get(0);
            if (!"OPEN".equals(q.get("status")) || ((java.sql.Timestamp) q.get("expires_at")).toInstant().isBefore(Instant.now())) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "This payment request is no longer open.");
            payee = (UUID) q.get("requester_id");
            UUID target = (UUID) q.get("target_user_id");
            if (target != null && !target.equals(userId)) throw ApiException.notFound("Payment request not found.");
            amount = (BigDecimal) q.get("amount"); currency = (String) q.get("currency"); requestId = (UUID) q.get("id");
        } else {
            if (r.amount() == null) throw ApiException.badRequest("AMOUNT_REQUIRED", "Enter an amount.");
            payee = userByHandle(r.toHandle()).orElseThrow(() -> ApiException.notFound("No FlowPilot user has that handle."));
            amount = r.amount(); currency = null;
        }
        if (payee.equals(userId)) throw ApiException.badRequest("SELF_PAYMENT", "You can't pay yourself.");
        if (amount.compareTo(maxAmount()) > 0) throw ApiException.badRequest("AMOUNT_TOO_LARGE", "Simulated payments are limited to " + maxAmount().toPlainString() + ".");
        Account from = accounts.findByIdAndUserId(r.fromAccountId(), userId).filter(a -> a.getArchivedAt() == null).orElseThrow(() -> ApiException.notFound("Account not found."));
        if (currency != null && !currency.equals(from.getCurrency())) throw ApiException.badRequest("CURRENCY_MISMATCH", "Pay from an account in " + currency + ".");
        currency = from.getCurrency();
        if (!from.isAllowNegative() && from.getCurrentBalance().compareTo(amount) < 0) throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Not enough funds in " + from.getName() + ".");
        if (receiveAccount(payee, currency) == null) throw new ApiException(HttpStatus.CONFLICT, "RECIPIENT_CANNOT_RECEIVE", "That person can't receive " + currency + " right now.");

        for (int i = 0; i < 5; i++) {
            try {
                UUID id = jdbc.queryForObject("insert into payments (reference, request_id, payer_id, payee_id, payer_account_id, amount, currency, note, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                        UUID.class, reference(), requestId, userId, payee, from.getId(), amount, currency, note, java.sql.Timestamp.from(Instant.now().plus(Duration.ofMinutes(settings.integer("payment.pending_ttl_minutes", 15)))));
                event(id, null, "PENDING", userId, "Created — waiting for your confirmation");
                if (key != null) jdbc.update("insert into idempotency_keys (user_id, key, operation, request_hash, resource_id) values (?, ?, 'INITIATE_PAYMENT', ?, ?)", userId, key, hash, id);
                return get(userId, id).payment();
            } catch (DataIntegrityViolationException e) {
                if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_payments_active_request")) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAYING", "This request already has a payment in progress or completed.");
                if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_payments_reference")) continue;
                throw e;
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFERENCE_UNAVAILABLE", "Please try again.");
    }

    /** Not @Transactional on purpose: a business failure must be able to record FAILED in its own transaction after the money transaction rolls back. */
    public PaymentView confirm(UUID userId, UUID paymentId) {
        try {
            tx.execute(st -> { doConfirm(userId, paymentId); return null; });
        } catch (ApiException e) {
            if (e.code().equals("INSUFFICIENT_FUNDS") || e.code().equals("RECIPIENT_CANNOT_RECEIVE") || e.code().equals("NOT_FOUND")) markFailed(paymentId, userId, e.getMessage());
            throw e;
        }
        return get(userId, paymentId).payment();
    }

    private void doConfirm(UUID userId, UUID paymentId) {
        var rows = jdbc.queryForList("select * from payments where id = ? and payer_id = ? for update", paymentId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Payment not found.");
        var p = rows.get(0);
        String status = (String) p.get("status");
        if (status.equals("SUCCESSFUL")) return;   // safe repeat: confirming twice never moves money twice
        if (!status.equals("PENDING")) throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "This payment can no longer be confirmed (" + status.toLowerCase() + ").");
        if (((java.sql.Timestamp) p.get("expires_at")).toInstant().isBefore(Instant.now())) {
            transition(paymentId, "PENDING", "CANCELLED", userId, "Expired before confirmation", null);
            return;
        }
        UUID payee = (UUID) p.get("payee_id"), payerAccount = (UUID) p.get("payer_account_id");
        BigDecimal amount = (BigDecimal) p.get("amount"); String currency = (String) p.get("currency"), ref = (String) p.get("reference");
        UUID payeeAccount = receiveAccount(payee, currency);
        if (payeeAccount == null) throw new ApiException(HttpStatus.CONFLICT, "RECIPIENT_CANNOT_RECEIVE", "That person can't receive " + currency + " right now.");

        // Canonical lock order across both users' accounts: two opposite payments can never deadlock.
        List<UUID> ids = new ArrayList<>(List.of(payerAccount, payeeAccount)); Collections.sort(ids);
        for (UUID id : ids) accounts.findActiveForUpdate(id, id.equals(payerAccount) ? userId : payee).orElseThrow(() -> ApiException.notFound("Account not found."));

        String payerName = nameOf(userId), payeeName = nameOf(payee);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var out = ledger.post(userId, new PostTransactionRequest(TransactionType.EXPENSE, amount, today, payerAccount, null, null, "Simulated payment to " + payeeName, payeeName, "simulated", "Ref " + ref), null);
        var in = ledger.post(payee, new PostTransactionRequest(TransactionType.INCOME, amount, today, payeeAccount, null, null, "Simulated payment from " + payerName, payerName, "simulated", "Ref " + ref), null);
        jdbc.update("update payments set status = 'SUCCESSFUL', payee_account_id = ?, payer_tx_id = ?, payee_tx_id = ?, failure_reason = null, updated_at = now() where id = ?", payeeAccount, out.id(), in.id(), paymentId);
        event(paymentId, "PENDING", "SUCCESSFUL", userId, "Confirmed — simulated money moved in both ledgers");
        UUID requestId = (UUID) p.get("request_id");
        if (requestId != null) jdbc.update("update payment_requests set status = 'PAID' where id = ?", requestId);

        String shown = amount.toPlainString() + " " + currency;
        afterCommit(() -> {
            notifyUser(payee, "Simulated payment received", shown + " from " + payerName + " (" + ref + ")", "pay-in:" + paymentId);
            notifyUser(userId, "Simulated payment sent", shown + " to " + payeeName + " (" + ref + ")", "pay-out:" + paymentId);
            audit(userId, "PAYMENT_SUCCESSFUL", ref); audit(payee, "PAYMENT_RECEIVED", ref);
        });
    }

    private void markFailed(UUID paymentId, UUID userId, String reason) {
        tx.execute(st -> { transition(paymentId, "PENDING", "FAILED", userId, reason, reason); return null; });
        afterCommit(() -> audit(userId, "PAYMENT_FAILED", paymentId.toString()));
    }

    @Transactional
    public PaymentView cancel(UUID userId, UUID id) {
        if (jdbc.queryForObject("select count(*) from payments where id = ? and payer_id = ?", Integer.class, id, userId) == 0) throw ApiException.notFound("Payment not found.");
        if (!transition(id, "PENDING", "CANCELLED", userId, "Cancelled by payer", null)) throw new ApiException(HttpStatus.CONFLICT, "NOT_CANCELLABLE", "Only a pending payment can be cancelled.");
        afterCommit(() -> audit(userId, "PAYMENT_CANCELLED", id.toString()));
        return get(userId, id).payment();
    }

    /** An explicit, traceable financial operation: new ledger transactions on both sides, a refund row, and a status event. */
    @Transactional
    public PaymentView refund(UUID userId, UUID id, String reason) {
        refundInternal(userId, id, userId, reason);
        return get(userId, id).payment();
    }

    /** Privileged intervention by an administrator. A reason is mandatory; the caller audits it. Same ledger rules as a user refund. */
    @Transactional
    public void adminRefund(UUID adminId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "A reason is required for an administrative refund.");
        refundInternal(adminId, id, null, "Administrative refund: " + reason.trim());
    }

    @Transactional
    public void adminRejectDispute(UUID adminId, UUID paymentId, String note) {
        if (note == null || note.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "Explain the decision to the customer.");
        if (jdbc.update("update payment_disputes set status = 'RESOLVED_REJECTED', resolved_by = ?, resolved_at = now(), resolution_note = ? where payment_id = ? and status = 'OPEN'", adminId, note.trim(), paymentId) == 0) throw ApiException.notFound("No open dispute for that payment.");
        UUID payer = jdbc.queryForObject("select payer_id from payments where id = ?", UUID.class, paymentId);
        afterCommit(() -> notifyUser(payer, "Your payment dispute was reviewed", note.trim(), "dispute-rejected:" + paymentId));
    }

    /** requirePayee == null means "an administrator is acting"; otherwise only that user (the recipient) may refund. */
    private void refundInternal(UUID initiator, UUID id, UUID requirePayee, String reason) {
        var rows = requirePayee == null ? jdbc.queryForList("select * from payments where id = ? for update", id) : jdbc.queryForList("select * from payments where id = ? and payee_id = ? for update", id, requirePayee);
        if (rows.isEmpty()) throw ApiException.notFound("Payment not found.");
        var p = rows.get(0);
        if (!"SUCCESSFUL".equals(p.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "NOT_REFUNDABLE", "Only a successful payment can be refunded, and only once.");
        UUID payer = (UUID) p.get("payer_id"), payee = (UUID) p.get("payee_id"), payeeAccount = (UUID) p.get("payee_account_id"), payerAccount = (UUID) p.get("payer_account_id");
        BigDecimal amount = (BigDecimal) p.get("amount"); String ref = (String) p.get("reference");
        for (UUID a : List.of(payeeAccount, payerAccount)) {
            UUID owner = a.equals(payeeAccount) ? payee : payer;
            if (accounts.findByIdAndUserId(a, owner).filter(x -> x.getArchivedAt() == null).isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "REFUND_ACCOUNT_UNAVAILABLE", "An account involved in this payment is no longer active.");
        }
        List<UUID> ids = new ArrayList<>(List.of(payeeAccount, payerAccount)); Collections.sort(ids);
        for (UUID a : ids) accounts.findActiveForUpdate(a, a.equals(payeeAccount) ? payee : payer).orElseThrow();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String payerName = nameOf(payer), payeeName = nameOf(payee);
        var out = ledger.post(payee, new PostTransactionRequest(TransactionType.EXPENSE, amount, today, payeeAccount, null, null, "Refund to " + payerName + " (" + ref + ")", payerName, "simulated", "Refund of " + ref), null);
        var in = ledger.post(payer, new PostTransactionRequest(TransactionType.INCOME, amount, today, payerAccount, null, null, "Refund from " + payeeName + " (" + ref + ")", payeeName, "simulated", "Refund of " + ref), null);
        jdbc.update("insert into payment_refunds (payment_id, amount, reason, initiated_by, payee_tx_id, payer_tx_id) values (?, ?, ?, ?, ?, ?)", id, amount, blank(reason), initiator, out.id(), in.id());
        jdbc.update("update payments set status = 'REFUNDED', updated_at = now() where id = ?", id);
        event(id, "SUCCESSFUL", "REFUNDED", initiator, blank(reason) == null ? "Refunded by recipient" : "Refunded: " + reason.trim());
        jdbc.update("update payment_disputes set status = 'RESOLVED_REFUNDED', resolved_by = ?, resolved_at = now(), resolution_note = ? where payment_id = ? and status = 'OPEN'", initiator, blank(reason) == null ? "Refunded" : trunc(reason.trim(), 480), id);
        String shown = amount.toPlainString() + " " + p.get("currency");
        afterCommit(() -> { notifyUser(payer, "Simulated payment refunded", shown + " returned (" + ref + ")", "refund:" + id); notifyUser(payee, "A payment you received was refunded", shown + " (" + ref + ")", "refund-out:" + id); audit(initiator, "PAYMENT_REFUNDED", ref); });
    }

    @Transactional
    public void openDispute(UUID userId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "Tell us what went wrong.");
        var rows = jdbc.queryForList("select status, created_at, payee_id, reference from payments where id = ? and payer_id = ? for update", id, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Payment not found.");
        var p = rows.get(0);
        if (!"SUCCESSFUL".equals(p.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "NOT_DISPUTABLE", "Only a successful payment can be disputed.");
        if (((java.sql.Timestamp) p.get("created_at")).toInstant().isBefore(Instant.now().minus(DISPUTE_WINDOW))) throw new ApiException(HttpStatus.CONFLICT, "DISPUTE_WINDOW_CLOSED", "Disputes must be opened within 30 days.");
        try { jdbc.update("insert into payment_disputes (payment_id, opened_by, reason) values (?, ?, ?)", id, userId, reason.trim()); }
        catch (DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "DISPUTE_EXISTS", "A dispute is already open for this payment."); }
        UUID payee = (UUID) p.get("payee_id"); String ref = (String) p.get("reference");
        afterCommit(() -> { notifyUser(payee, "A payment was disputed", "Reference " + ref + ". You can refund it or wait for review.", "dispute:" + id); audit(userId, "PAYMENT_DISPUTE_OPENED", ref); });
    }

    @Transactional
    public void withdrawDispute(UUID userId, UUID id) {
        if (jdbc.update("update payment_disputes set status = 'WITHDRAWN', resolved_at = now(), resolved_by = ? where payment_id = ? and opened_by = ? and status = 'OPEN'", userId, id, userId) == 0) throw ApiException.notFound("No open dispute to withdraw.");
    }

    // ------------------------------------------------------------------ reads

    public PageOf<PaymentView> list(UUID userId, String status, int page, int size) {
        int s = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        boolean filter = status != null && status.matches("PENDING|SUCCESSFUL|FAILED|CANCELLED|REFUNDED");
        Long total = jdbc.queryForObject("select count(*) from payments p where (p.payer_id = ? or p.payee_id = ?)" + (filter ? " and p.status = ?" : ""), Long.class,
                filter ? new Object[]{userId, userId, status} : new Object[]{userId, userId});
        List<Object> args = new ArrayList<>(List.of(userId, userId, userId, userId)); if (filter) args.add(status); args.add(s); args.add((long) pg * s);
        List<PaymentView> items = jdbc.query(PAY_SQL + " where (p.payer_id = ? or p.payee_id = ?)" + (filter ? " and p.status = ?" : "") + " order by p.created_at desc limit ? offset ?", (rs, i) -> payView(rs), 
                filter ? new Object[]{userId, userId, userId, userId, status, s, (long) pg * s} : new Object[]{userId, userId, userId, userId, s, (long) pg * s});
        return new PageOf<>(items, pg, s, total == null ? 0 : total);
    }

    public PaymentDetail get(UUID userId, UUID id) {
        PaymentView v = jdbc.query(PAY_SQL + " where p.id = ? and (p.payer_id = ? or p.payee_id = ?)", (rs, i) -> payView(rs), userId, userId, id, userId, userId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        List<EventView> events = jdbc.query("select from_status, to_status, note, created_at from payment_events where payment_id = ? order by created_at",
                (rs, i) -> new EventView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), id);
        RefundView refund = jdbc.query("select amount, reason, created_at from payment_refunds where payment_id = ?", (rs, i) -> new RefundView(rs.getBigDecimal(1).toPlainString(), rs.getString(2), rs.getTimestamp(3).toInstant()), id).stream().findFirst().orElse(null);
        return new PaymentDetail(v, events, refund);
    }

    // ------------------------------------------------------------------ internals

    private static final String PAY_SQL = "select p.*, case when p.payer_id = ? then 'SENT' else 'RECEIVED' end as direction, cu.full_name as cp_name, ch.handle as cp_handle, r.reference as req_ref, "
            + "(select d.status from payment_disputes d where d.payment_id = p.id order by d.created_at desc limit 1) as dispute_status "
            + "from payments p join user_profiles cu on cu.user_id = (case when p.payer_id = ? then p.payee_id else p.payer_id end) "
            + "left join payment_profiles ch on ch.user_id = cu.user_id left join payment_requests r on r.id = p.request_id";

    private PaymentView payView(ResultSet rs) throws SQLException {
        String dir = rs.getString("direction"), status = rs.getString("status"), dispute = rs.getString("dispute_status");
        Instant created = rs.getTimestamp("created_at").toInstant(), expires = rs.getTimestamp("expires_at").toInstant();
        boolean pendingLive = status.equals("PENDING") && expires.isAfter(Instant.now());
        return new PaymentView(rs.getObject("id", UUID.class), rs.getString("reference"), dir, rs.getString("cp_name"), rs.getString("cp_handle"), rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("note"), status, rs.getString("failure_reason"), rs.getString("req_ref"), dir.equals("SENT") && pendingLive, dir.equals("SENT") && pendingLive,
                dir.equals("RECEIVED") && status.equals("SUCCESSFUL"), dir.equals("SENT") && status.equals("SUCCESSFUL") && created.isAfter(Instant.now().minus(DISPUTE_WINDOW)) && !"OPEN".equals(dispute),
                dispute, created, rs.getTimestamp("updated_at").toInstant(), true);
    }

    private static final String REQ_SQL = "select r.*, rq.full_name as requester_name, tg.full_name as target_name from payment_requests r join user_profiles rq on rq.user_id = r.requester_id left join user_profiles tg on tg.user_id = r.target_user_id";

    private RequestView reqView(ResultSet rs, UUID me) throws SQLException {
        boolean mine = rs.getObject("requester_id", UUID.class).equals(me);
        String status = rs.getString("status"); Instant exp = rs.getTimestamp("expires_at").toInstant();
        if (status.equals("OPEN") && exp.isBefore(Instant.now())) status = "EXPIRED";
        return new RequestView(rs.getObject("id", UUID.class), rs.getString("reference"), mine ? "CREATED" : "INCOMING", mine ? (rs.getString("target_name") == null ? "Anyone with the link" : rs.getString("target_name")) : rs.getString("requester_name"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), rs.getString("note"), status, exp, rs.getTimestamp("created_at").toInstant());
    }

    private RequestView requestView(UUID id, UUID me) {
        return jdbc.query(REQ_SQL + " where r.id = ?", (rs, i) -> reqView(rs, me), id).stream().findFirst().orElseThrow();
    }

    private boolean transition(UUID id, String from, String to, UUID actor, String note, String failure) {
        int n = jdbc.update("update payments set status = ?, failure_reason = ?, updated_at = now() where id = ? and status = ?", to, failure, id, from);
        if (n == 1) event(id, from, to, actor, note);
        return n == 1;
    }

    private void event(UUID paymentId, String from, String to, UUID actor, String note) {
        jdbc.update("insert into payment_events (payment_id, from_status, to_status, actor_user_id, note) values (?, ?, ?, ?, ?)", paymentId, from, to, actor, note);
    }

    private UUID receiveAccount(UUID user, String currency) {
        List<UUID> pref = jdbc.query("select a.id from payment_profiles p join accounts a on a.id = p.receive_account_id where p.user_id = ? and a.currency = ? and a.archived_at is null", (rs, i) -> rs.getObject(1, UUID.class), user, currency);
        if (!pref.isEmpty()) return pref.get(0);
        List<UUID> any = jdbc.query("select id from accounts where user_id = ? and currency = ? and archived_at is null order by created_at limit 1", (rs, i) -> rs.getObject(1, UUID.class), user, currency);
        return any.isEmpty() ? null : any.get(0);
    }

    private Optional<UUID> userByHandle(String handle) {
        return jdbc.query("select ph.user_id from payment_profiles ph join users u on u.id = ph.user_id where ph.handle = ? and u.status = 'ACTIVE' and u.role = 'USER'", (rs, i) -> rs.getObject(1, UUID.class), handle).stream().findFirst();
    }

    private String nameOf(UUID user) { return jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, user); }

    private void notifyUser(UUID user, String title, String body, String dedupe) {
        notifications.notify(user, "PAYMENT", title, body, "/app/payments", dedupe);
        live.push(user, "payments");
    }

    private void audit(UUID actor, String type, String detail) { audit.record(Role.USER, type, true, actor, null, null, "simulated; " + detail); }

    BigDecimal maxAmount() { return settings.decimal("payment.max_amount", "100000"); }

    private String reference() { return "FP-" + randomChars(10); }

    private String randomChars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { safely(r); } });
        } else safely(r);
    }

    /** Post-commit side effects (notifications, audit, email) must never turn a committed success into an error response. */
    private static void safely(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(PaymentService.class).error("Post-commit action failed", e); }
    }
}
