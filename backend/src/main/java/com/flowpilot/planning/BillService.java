package com.flowpilot.planning;

import com.flowpilot.audit.FinancialAudit;
import com.flowpilot.common.ApiException;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.FinanceDtos.TransactionView;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.planning.PlanningDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {
    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    private final FinancialAudit fin;

    public BillService(JdbcTemplate jdbc, LedgerService ledger, FinancialAudit fin) {
        this.fin = fin;
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    public BillsOverview overview(UUID userId) {
        List<BillView> bills = query(userId, null, null);
        // Estimates count ACTIVE items only and are grouped per currency (currencies are never added together).
        Map<String, BigDecimal> monthly = new TreeMap<>();
        for (BillView b : bills) {
            if (b.status().equals("ACTIVE")) monthly.merge(b.currency(), new BigDecimal(b.monthlyEstimate()), BigDecimal::add);
        }
        List<CostEstimate> estimates = monthly.entrySet().stream().map(e -> new CostEstimate(e.getKey(),
                e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                e.getValue().multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP).toPlainString())).toList();
        return new BillsOverview(bills, estimates);
    }

    /** Soonest ACTIVE items for the dashboard widget. */
    public List<BillView> upcoming(UUID userId, int limit) {
        return query(userId, "b.status = 'ACTIVE'", Math.min(Math.max(limit, 1), 20));
    }

    @Transactional
    public BillView create(UUID userId, BillRequest r) {
        checkRefs(userId, r.categoryId(), r.accountId());
        UUID id = jdbc.queryForObject("insert into bills (user_id, name, kind, amount, currency, frequency, next_due_date, category_id, account_id, reminder_days_before, notes) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", UUID.class, userId, r.name().trim(), r.kind(), r.amount(), r.currency(),
                r.frequency(), r.nextDueDate(), r.categoryId(), r.accountId(), r.reminderDaysBefore() == null ? 3 : r.reminderDaysBefore(), blankToNull(r.notes()));
        fin.record(userId, userId, "BILL_CREATED", "BILL", id, r.name().trim() + " " + r.amount().toPlainString() + " " + r.currency() + " " + r.frequency(), null, "ACTIVE");
        return one(userId, id);
    }

    /** Price changes are recorded, never overwritten silently; the item is flagged NEEDS_REVIEW so the user confirms it. */
    @Transactional
    public BillView update(UUID userId, UUID id, BillRequest r) {
        checkRefs(userId, r.categoryId(), r.accountId());
        List<Map<String, Object>> cur = jdbc.queryForList("select amount, currency from bills where id = ? and user_id = ? and archived_at is null for update", id, userId);
        if (cur.isEmpty()) throw ApiException.notFound("Bill not found.");
        BigDecimal old = (BigDecimal) cur.get(0).get("amount");
        boolean priceChanged = old.compareTo(r.amount()) != 0;
        jdbc.update("update bills set name = ?, kind = ?, amount = ?, currency = ?, frequency = ?, next_due_date = ?, category_id = ?, account_id = ?, reminder_days_before = ?, notes = ?, "
                        + "status = case when ? and status = 'ACTIVE' then 'NEEDS_REVIEW' else status end where id = ? and user_id = ?",
                r.name().trim(), r.kind(), r.amount(), r.currency(), r.frequency(), r.nextDueDate(), r.categoryId(), r.accountId(),
                r.reminderDaysBefore() == null ? 3 : r.reminderDaysBefore(), blankToNull(r.notes()), priceChanged, id, userId);
        if (priceChanged) jdbc.update("insert into bill_price_changes (bill_id, old_amount, new_amount) values (?, ?, ?)", id, old, r.amount());
        fin.record(userId, userId, priceChanged ? "BILL_PRICE_CHANGED" : "BILL_UPDATED", "BILL", id, r.name().trim(), old.toPlainString(), r.amount().toPlainString());
        return one(userId, id);
    }

    @Transactional
    public BillView setStatus(UUID userId, UUID id, String status) {
        if (jdbc.update("update bills set status = ? where id = ? and user_id = ? and archived_at is null", status, id, userId) == 0) throw ApiException.notFound("Bill not found.");
        return one(userId, id);
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        if (jdbc.update("update bills set archived_at = now() where id = ? and user_id = ? and archived_at is null", id, userId) == 0) throw ApiException.notFound("Bill not found.");
    }

    /**
     * Pays the current cycle: one real EXPENSE in the ledger + advance the due date, atomically.
     * The idempotency key (bill id + due date) makes a double-click or retry a no-op for the same cycle.
     */
    @Transactional
    public TransactionView pay(UUID userId, UUID id, UUID accountId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select name, amount, frequency, next_due_date, status, category_id from bills where id = ? and user_id = ? and archived_at is null for update", id, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Bill not found.");
        Map<String, Object> b = rows.get(0);
        String status = (String) b.get("status");
        if (status.equals("CANCELLED") || status.equals("PAUSED")) throw new ApiException(HttpStatus.CONFLICT, "NOT_PAYABLE", "Resume this item before paying it.");
        LocalDate due = ((java.sql.Date) b.get("next_due_date")).toLocalDate();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (due.isAfter(today.plusDays(35))) throw new ApiException(HttpStatus.CONFLICT, "NOT_DUE_YET", "This item is not due yet.");

        UUID categoryId = (UUID) b.get("category_id");
        TransactionView tx = ledger.post(userId, new PostTransactionRequest(TransactionType.EXPENSE, (BigDecimal) b.get("amount"), today, accountId, null,
                categoryId, ("Bill: " + (String) b.get("name")), (String) b.get("name"), "bill", null), "bill-" + id + "-" + due);
        fin.record(userId, userId, "BILL_PAID", "BILL", id, (String) b.get("name") + " paid via transaction " + tx.id(), due.toString(), BillMath.advance(due, (String) b.get("frequency")).toString());
        jdbc.update("update bills set next_due_date = ?, last_paid_on = ? where id = ? and user_id = ? and next_due_date = ?",
                BillMath.advance(due, (String) b.get("frequency")), today, id, userId, due);
        return tx;
    }

    // ------------------------------------------------------------------ internals


    private BillView one(UUID userId, UUID id) {
        return query(userId, "b.id = '" + id + "'", 1).stream().findFirst().orElseThrow(() -> ApiException.notFound("Bill not found."));
    }

    /** {@code extraWhere} is only ever built from server-side constants or a parsed UUID, never raw user text. */
    private List<BillView> query(UUID userId, String extraWhere, Integer limit) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String sql = "select b.*, (select pc.old_amount from bill_price_changes pc where pc.bill_id = b.id order by pc.changed_at desc limit 1) as previous_amount "
                + "from bills b where b.user_id = ? and b.archived_at is null" + (extraWhere == null ? "" : " and " + extraWhere)
                + " order by b.next_due_date asc, b.name" + (limit == null ? "" : " limit " + limit);
        return jdbc.query(sql, (rs, i) -> {
            LocalDate due = rs.getObject("next_due_date", LocalDate.class);
            long days = ChronoUnit.DAYS.between(today, due);
            int reminder = rs.getInt("reminder_days_before");
            BigDecimal amount = rs.getBigDecimal("amount");
            String freq = rs.getString("frequency");
            BigDecimal prev = rs.getBigDecimal("previous_amount");
            return new BillView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), amount.toPlainString(), rs.getString("currency"),
                    freq, due, rs.getString("status"), rs.getObject("category_id", UUID.class), rs.getObject("account_id", UUID.class), reminder,
                    rs.getString("notes"), rs.getObject("last_paid_on", LocalDate.class), days, days >= 0 && days <= reminder, days < 0,
                    BillMath.monthly(amount, freq).setScale(2, RoundingMode.HALF_UP).toPlainString(), prev == null ? null : prev.toPlainString());
        }, userId);
    }

    private void checkRefs(UUID userId, UUID categoryId, UUID accountId) {
        if (categoryId != null) {
            Integer n = jdbc.queryForObject("select count(*) from categories where id = ? and kind = 'EXPENSE' and (user_id is null or user_id = ?)", Integer.class, categoryId, userId);
            if (n == null || n == 0) throw ApiException.badRequest("INVALID_CATEGORY", "Choose an expense category.");
        }
        if (accountId != null) {
            Integer n = jdbc.queryForObject("select count(*) from accounts where id = ? and user_id = ? and archived_at is null", Integer.class, accountId, userId);
            if (n == null || n == 0) throw ApiException.badRequest("INVALID_ACCOUNT", "Choose one of your accounts.");
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
