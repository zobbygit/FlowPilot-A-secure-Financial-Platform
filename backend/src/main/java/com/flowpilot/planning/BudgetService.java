package com.flowpilot.planning;

import com.flowpilot.audit.FinancialAudit;
import com.flowpilot.common.ApiException;
import com.flowpilot.planning.PlanningDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spending is always derived from the ledger, never stored, so budgets can't drift from reality. */
@Service
public class BudgetService {
    private static final String SPENT_SQL = "select coalesce(sum(-e.amount), 0) from ledger_entries e join transactions t on t.id = e.transaction_id "
            + "where e.user_id = ? and e.currency = ? and t.type = 'EXPENSE' and t.status = 'POSTED' "
            + "and t.reverses_transaction_id is null and t.archived_at is null and t.occurred_on between ? and ?";

    private final JdbcTemplate jdbc;

    private final FinancialAudit fin;

    public BudgetService(JdbcTemplate jdbc, FinancialAudit fin) {
        this.fin = fin; this.jdbc = jdbc; }

    public List<BudgetView> list(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return jdbc.query("select b.*, c.name as category_name from budgets b left join categories c on c.id = b.category_id "
                        + "where b.user_id = ? and b.archived_at is null and b.household_id is null order by b.created_at",
                (rs, i) -> view(userId, today, rs), userId);
    }

    @Transactional
    public BudgetView create(UUID userId, BudgetRequest r) {
        if ("CUSTOM".equals(r.periodType()) && (r.startDate() == null || r.endDate() == null || r.endDate().isBefore(r.startDate()))) {
            throw ApiException.badRequest("INVALID_PERIOD", "A custom budget needs a start date and an end date on or after it.");
        }
        if (r.categoryId() != null) {
            Integer ok = jdbc.queryForObject("select count(*) from categories where id = ? and kind = 'EXPENSE' and (user_id is null or user_id = ?)",
                    Integer.class, r.categoryId(), userId);
            if (ok == null || ok == 0) throw ApiException.badRequest("INVALID_CATEGORY", "Choose an expense category.");
        }
        List<Integer> th = r.thresholds() == null || r.thresholds().isEmpty() ? List.of(80, 100) : r.thresholds().stream().distinct().sorted().toList();
        UUID id = jdbc.queryForObject("insert into budgets (user_id, name, period_type, start_date, end_date, category_id, limit_amount, currency, rollover, alert_thresholds) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", UUID.class, userId, r.name().trim(), r.periodType(),
                "CUSTOM".equals(r.periodType()) ? r.startDate() : null, "CUSTOM".equals(r.periodType()) ? r.endDate() : null,
                r.categoryId(), r.limitAmount(), r.currency(), r.rollover(), th.stream().map(String::valueOf).collect(Collectors.joining(",")));
        fin.record(userId, userId, "BUDGET_CREATED", "BUDGET", id, r.name().trim() + " limit " + r.limitAmount().toPlainString() + " " + r.currency(), null, r.periodType());
        return list(userId).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        if (jdbc.update("update budgets set archived_at = now() where id = ? and user_id = ? and archived_at is null", id, userId) == 0) {
            throw ApiException.notFound("Budget not found.");
        }
        fin.record(userId, userId, "BUDGET_ARCHIVED", "BUDGET", id, null, "ACTIVE", "ARCHIVED");
    }

    private BudgetView view(UUID userId, LocalDate today, java.sql.ResultSet rs) throws java.sql.SQLException {
        String type = rs.getString("period_type");
        LocalDate start = "MONTHLY".equals(type) ? today.withDayOfMonth(1) : rs.getObject("start_date", LocalDate.class);
        LocalDate end = "MONTHLY".equals(type) ? today.withDayOfMonth(today.lengthOfMonth()) : rs.getObject("end_date", LocalDate.class);
        String currency = rs.getString("currency");
        UUID categoryId = rs.getObject("category_id", UUID.class);
        BigDecimal limit = rs.getBigDecimal("limit_amount");
        boolean rollover = rs.getBoolean("rollover");

        BigDecimal spent = spent(userId, currency, start, end, categoryId);
        // Rollover (monthly only): unspent money from the PREVIOUS month is added once; it does not compound further back.
        BigDecimal carried = BigDecimal.ZERO;
        if (rollover && "MONTHLY".equals(type)) {
            LocalDate pStart = start.minusMonths(1), pEnd = start.minusDays(1);
            carried = limit.subtract(spent(userId, currency, pStart, pEnd, categoryId)).max(BigDecimal.ZERO);
        }
        BigDecimal available = limit.add(carried);
        BigDecimal remaining = available.subtract(spent);
        double pct = available.signum() == 0 ? 0 : spent.multiply(BigDecimal.valueOf(100)).divide(available, 1, RoundingMode.HALF_UP).doubleValue();
        List<Integer> thresholds = Arrays.stream(rs.getString("alert_thresholds").split(",")).map(Integer::parseInt).toList();
        List<Integer> crossed = thresholds.stream().filter(t -> pct >= t).toList();
        String state = pct >= 100 ? "EXCEEDED" : pct >= thresholds.stream().min(Integer::compare).orElse(80) ? "WARNING" : "OK";
        return new BudgetView(rs.getObject("id", UUID.class), rs.getString("name"), type, start, end, categoryId, rs.getString("category_name"),
                limit.toPlainString(), currency, rollover, thresholds, carried.toPlainString(), spent.toPlainString(), remaining.toPlainString(), pct, state, crossed);
    }

    private BigDecimal spent(UUID userId, String currency, LocalDate from, LocalDate to, UUID categoryId) {
        String sql = SPENT_SQL + (categoryId == null ? "" : " and t.category_id = ?");
        Object[] args = categoryId == null ? new Object[]{userId, currency, from, to} : new Object[]{userId, currency, from, to, categoryId};
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }
}
