package com.flowpilot.reports;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feeds the Financial Activity Pipeline. Every number is computed from the ledger and planning tables - nothing is invented. */
@Service
public class PipelineService {
    public record Related(UUID id, LocalDate date, String title, String amount) {}
    public record Stage(String id, String title, String amount, String secondary, String explanation, boolean liveActivity, List<Related> related) {}
    public record Pipeline(String currency, LocalDate from, LocalDate to, List<Stage> stages) {}

    public record SafeToSpend(String currency, String liquid, String upcomingBills, String reservedAllocations, String safeToSpend, String perDayRemainingMonth, int daysLeft, List<String> notes) {}

    /**
     * Safe-to-spend = spendable money (bank, cash, wallet; NOT savings accounts) - active bills due before month end - money your
     * automation rules have already planned/reserved. It is a planning aid built from stated parts, not a guarantee.
     */
    @Transactional(readOnly = true)
    public SafeToSpend safeToSpend(UUID u, String currencyParam) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC), end = today.withDayOfMonth(today.lengthOfMonth());
        String cur = currencyParam != null && currencyParam.matches("[A-Z]{3}") ? currencyParam : jdbc.queryForObject("select preferred_currency from user_profiles where user_id = ?", String.class, u);
        BigDecimal liquid = nz(jdbc.queryForObject("select coalesce(sum(current_balance), 0) from accounts where user_id = ? and currency = ? and archived_at is null and type <> 'SAVINGS'", BigDecimal.class, u, cur));
        BigDecimal bills = nz(jdbc.queryForObject("select coalesce(sum(amount), 0) from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null and next_due_date between ? and ?", BigDecimal.class, u, cur, today, end));
        BigDecimal reserved = nz(jdbc.queryForObject("select coalesce(sum(amount), 0) from automation_allocations where user_id = ? and currency = ? and status = 'PLANNED'", BigDecimal.class, u, cur));
        BigDecimal safe = liquid.subtract(bills).subtract(reserved);
        int daysLeft = end.getDayOfMonth() - today.getDayOfMonth() + 1;
        BigDecimal perDay = safe.signum() > 0 ? safe.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.DOWN) : BigDecimal.ZERO;
        List<String> notes = new ArrayList<>(List.of("Spendable balance counts bank, cash and wallet accounts in " + cur + "; savings accounts are excluded.",
                "Active bills and subscriptions due through " + end + " are set aside, and so are allocations your automation rules have planned.",
                "Income you haven't received yet is not counted. This is a planning aid, not a guarantee."));
        if (safe.signum() < 0) notes.add("Your commitments currently exceed your spendable balance.");
        return new SafeToSpend(cur, m(liquid), m(bills), m(reserved), m(safe), m(perDay), daysLeft, notes);
    }

    private static final String FLOW = "from ledger_entries e join transactions t on t.id = e.transaction_id where e.user_id = ? and e.currency = ? and t.status = 'POSTED' "
            + "and t.reverses_transaction_id is null and t.archived_at is null";

    private final JdbcTemplate jdbc;

    public PipelineService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Pipeline build(UUID u, LocalDate from, LocalDate to, String currencyParam) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate f = from == null ? today.withDayOfMonth(1) : from, t = to == null ? today : to;
        if (t.isBefore(f) || ChronoUnit.DAYS.between(f, t) > 366) throw ApiException.badRequest("INVALID_RANGE", "Choose a range of at most one year.");
        String cur = currencyParam != null && currencyParam.matches("[A-Z]{3}") ? currencyParam : jdbc.queryForObject("select preferred_currency from user_profiles where user_id = ?", String.class, u);
        Instant dayAgo = Instant.now().minus(1, ChronoUnit.DAYS);

        BigDecimal income = sum(u, cur, f, t, "INCOME"), expenses = sum(u, cur, f, t, "EXPENSE").negate();
        BigDecimal balance = nz(jdbc.queryForObject("select coalesce(sum(current_balance), 0) from accounts where user_id = ? and currency = ? and archived_at is null", BigDecimal.class, u, cur));
        BigDecimal commitments = nz(jdbc.queryForObject("select coalesce(sum(amount), 0) from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null and next_due_date between ? and ?", BigDecimal.class, u, cur, today, today.plusDays(30)));
        BigDecimal budgetLimit = nz(jdbc.queryForObject("select coalesce(sum(limit_amount), 0) from budgets where user_id = ? and currency = ? and archived_at is null and period_type = 'MONTHLY' and household_id is null", BigDecimal.class, u, cur));
        BigDecimal budgetSpent = nz(jdbc.queryForObject("select coalesce(sum(-e.amount), 0) " + FLOW + " and t.type = 'EXPENSE' and t.occurred_on between ? and ? and (t.category_id in (select category_id from budgets where user_id = ? and archived_at is null and category_id is not null and period_type = 'MONTHLY') or exists (select 1 from budgets b where b.user_id = ? and b.currency = ? and b.archived_at is null and b.category_id is null and b.period_type = 'MONTHLY'))",
                BigDecimal.class, u, cur, today.withDayOfMonth(1), today, u, u, cur));
        BigDecimal goalsSaved = nz(jdbc.queryForObject("select coalesce(sum(c.amount), 0) from goal_contributions c join savings_goals g on g.id = c.goal_id where g.user_id = ? and g.currency = ? and c.kind = 'TRANSFERRED' and g.status <> 'ARCHIVED'", BigDecimal.class, u, cur));
        BigDecimal goalsPlanned = nz(jdbc.queryForObject("select coalesce(sum(c.amount), 0) from goal_contributions c join savings_goals g on g.id = c.goal_id where g.user_id = ? and g.currency = ? and c.kind = 'PLANNED' and g.status <> 'ARCHIVED'", BigDecimal.class, u, cur));
        BigDecimal simulated = nz(jdbc.queryForObject("select coalesce(sum(-e.amount), 0) " + FLOW + " and t.type = 'EXPENSE' and t.payment_method = 'simulated' and t.occurred_on between ? and ?", BigDecimal.class, u, cur, f, t));
        BigDecimal available = balance.subtract(commitments);

        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("income", "Incoming income", m(income), null, "Money received between " + f + " and " + t + ", including simulated payments you received.", live(u, cur, "INCOME", dayAgo), related(u, cur, f, t, "INCOME")));
        stages.add(new Stage("balances", "Account balances", m(balance), null, "Today's total across your active " + cur + " accounts.", false, List.of()));
        stages.add(new Stage("commitments", "Reserved commitments", m(commitments), null, "Active bills and subscriptions due in the next 30 days. They are not spent yet, but they are spoken for.", false, upcomingBills(u, cur, today)));
        stages.add(new Stage("budgets", "Budget allocations", m(budgetLimit), m(budgetSpent) + " spent this month", "Sum of your monthly budget limits, and how much of the covered spending has been used so far this month.", false, List.of()));
        stages.add(new Stage("goals", "Savings goals", m(goalsSaved), m(goalsPlanned) + " planned, not yet moved", "Saved counts real transfers only. Planned allocations are intentions and have not left your accounts.", false, List.of()));
        stages.add(new Stage("expenses", "Expenses & simulated payments", m(expenses), m(simulated) + " of it simulated payments", "Everything spent in the period, including simulated payments you sent (no real money).", live(u, cur, "EXPENSE", dayAgo), related(u, cur, f, t, "EXPENSE")));
        stages.add(new Stage("available", "Remaining available funds", m(available), null, "Balance minus the commitments due in the next 30 days" + (available.signum() < 0 ? " — currently negative." : "."), false, List.of()));
        return new Pipeline(cur, f, t, stages);
    }

    private BigDecimal sum(UUID u, String cur, LocalDate f, LocalDate t, String type) {
        return nz(jdbc.queryForObject("select coalesce(sum(e.amount), 0) " + FLOW + " and t.type = ? and t.occurred_on between ? and ?", BigDecimal.class, u, cur, type, f, t));
    }

    private boolean live(UUID u, String cur, String type, Instant since) {
        Integer n = jdbc.queryForObject("select count(*) " + FLOW + " and t.type = ? and t.created_at >= ?", Integer.class, u, cur, type, java.sql.Timestamp.from(since));
        return n != null && n > 0;
    }

    private List<Related> related(UUID u, String cur, LocalDate f, LocalDate t, String type) {
        return jdbc.query("select t.id, t.occurred_on, coalesce(t.merchant, t.description, lower(t.type)) as title, sum(e.amount) as amount " + FLOW + " and t.type = ? and t.occurred_on between ? and ? "
                + "group by t.id, t.occurred_on, title, t.created_at order by t.occurred_on desc, t.created_at desc limit 5",
                (rs, i) -> new Related(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getBigDecimal(4).toPlainString()), u, cur, type, f, t);
    }

    private List<Related> upcomingBills(UUID u, String cur, LocalDate today) {
        return jdbc.query("select id, next_due_date, name, amount from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null and next_due_date between ? and ? order by next_due_date limit 5",
                (rs, i) -> new Related(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getBigDecimal(4).toPlainString()), u, cur, today, today.plusDays(30));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
