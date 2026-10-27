package com.flowpilot.reports;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Every figure is computed from the ledger on the server; exports reuse exactly these tables. */
@Service
public class ReportService {
    public record ReportTable(String type, String title, String currency, LocalDate from, LocalDate to, List<String> columns, List<List<String>> rows, List<String> notes) {}

    public static final List<String> TYPES = List.of("income-expense", "spending-by-category", "spending-by-merchant", "accounts",
            "subscription-cost", "budget-variance", "cash-flow-forecast");

    private static final String FLOW_WHERE = "e.user_id = ? and e.currency = ? and t.occurred_on between ? and ? and t.status = 'POSTED' "
            + "and t.reverses_transaction_id is null and t.archived_at is null";

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ReportTable build(UUID userId, String type, LocalDate from, LocalDate to, String currency, int horizonDays) {
        if (!TYPES.contains(type)) throw ApiException.notFound("Unknown report.");
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 3660) throw ApiException.badRequest("INVALID_RANGE", "Choose a valid date range (up to 10 years).");
        return switch (type) {
            case "income-expense" -> incomeExpense(userId, from, to, currency);
            case "spending-by-category" -> byCategory(userId, from, to, currency);
            case "spending-by-merchant" -> byMerchant(userId, from, to, currency);
            case "accounts" -> accounts(userId, from, to, currency);
            case "subscription-cost" -> subscriptions(userId, currency);
            case "budget-variance" -> budgetVariance(userId, currency);
            default -> forecast(userId, currency, horizonDays);
        };
    }

    private ReportTable incomeExpense(UUID u, LocalDate from, LocalDate to, String cur) {
        List<List<String>> rows = new ArrayList<>();
        BigDecimal ti = BigDecimal.ZERO, te = BigDecimal.ZERO;
        for (Map<String, Object> r : jdbc.queryForList("select to_char(t.occurred_on, 'YYYY-MM') as m, "
                + "coalesce(sum(case when t.type = 'INCOME' then e.amount else 0 end), 0) as inc, coalesce(sum(case when t.type = 'EXPENSE' then -e.amount else 0 end), 0) as exp "
                + "from ledger_entries e join transactions t on t.id = e.transaction_id where " + FLOW_WHERE + " and t.type in ('INCOME', 'EXPENSE') group by 1 order by 1", u, cur, from, to)) {
            BigDecimal inc = (BigDecimal) r.get("inc"), exp = (BigDecimal) r.get("exp");
            ti = ti.add(inc); te = te.add(exp);
            rows.add(List.of((String) r.get("m"), m(inc), m(exp), m(inc.subtract(exp))));
        }
        rows.add(List.of("Total", m(ti), m(te), m(ti.subtract(te))));
        return new ReportTable("income-expense", "Income versus expenses", cur, from, to, List.of("Month", "Income", "Expenses", "Net"), rows,
                List.of("Transfers and opening balances are excluded. Reversed transactions are excluded."));
    }

    private ReportTable byCategory(UUID u, LocalDate from, LocalDate to, String cur) {
        List<Map<String, Object>> data = jdbc.queryForList("select coalesce(c.name, 'Uncategorised') as name, sum(-e.amount) as total from ledger_entries e join transactions t on t.id = e.transaction_id "
                + "left join categories c on c.id = t.category_id where " + FLOW_WHERE + " and t.type = 'EXPENSE' group by 1 order by 2 desc", u, cur, from, to);
        BigDecimal sum = data.stream().map(r -> (BigDecimal) r.get("total")).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : data) rows.add(List.of((String) r.get("name"), m((BigDecimal) r.get("total")), pct((BigDecimal) r.get("total"), sum)));
        rows.add(List.of("Total", m(sum), sum.signum() == 0 ? "0.0%" : "100.0%"));
        return new ReportTable("spending-by-category", "Spending by category", cur, from, to, List.of("Category", "Spent", "Share"), rows, List.of());
    }

    private ReportTable byMerchant(UUID u, LocalDate from, LocalDate to, String cur) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select coalesce(min(nullif(trim(t.merchant), '')), 'Unspecified') as merchant, count(distinct t.id) as n, sum(-e.amount) as total "
                + "from ledger_entries e join transactions t on t.id = e.transaction_id where " + FLOW_WHERE + " and t.type = 'EXPENSE' "
                + "group by lower(coalesce(nullif(trim(t.merchant), ''), 'unspecified')) order by 3 desc limit 100", u, cur, from, to)) {
            rows.add(List.of((String) r.get("merchant"), String.valueOf(r.get("n")), m((BigDecimal) r.get("total"))));
        }
        return new ReportTable("spending-by-merchant", "Spending by merchant", cur, from, to, List.of("Merchant", "Transactions", "Spent"), rows,
                List.of("Merchants are grouped case-insensitively by the name you entered. Top 100 shown."));
    }

    private ReportTable accounts(UUID u, LocalDate from, LocalDate to, String cur) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select a.name, a.type, a.current_balance, "
                + "coalesce(sum(case when e.amount > 0 and t.occurred_on between ? and ? then e.amount end), 0) as inflow, "
                + "coalesce(sum(case when e.amount < 0 and t.occurred_on between ? and ? then -e.amount end), 0) as outflow "
                + "from accounts a left join ledger_entries e on e.account_id = a.id left join transactions t on t.id = e.transaction_id and t.status = 'POSTED' "
                + "where a.user_id = ? and a.currency = ? and a.archived_at is null group by a.id order by a.created_at", from, to, from, to, u, cur)) {
            rows.add(List.of((String) r.get("name"), ((String) r.get("type")).toLowerCase(), m((BigDecimal) r.get("inflow")), m((BigDecimal) r.get("outflow")), m((BigDecimal) r.get("current_balance"))));
        }
        return new ReportTable("accounts", "Account summaries", cur, from, to, List.of("Account", "Type", "Money in", "Money out", "Current balance"), rows,
                List.of("Money in/out includes transfers between your own accounts."));
    }

    private ReportTable subscriptions(UUID u, String cur) {
        List<List<String>> rows = new ArrayList<>();
        BigDecimal monthly = BigDecimal.ZERO;
        for (Map<String, Object> r : jdbc.queryForList("select name, kind, frequency, amount from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null order by amount desc", u, cur)) {
            BigDecimal mo = com.flowpilot.planning.BillMath.monthly((BigDecimal) r.get("amount"), (String) r.get("frequency"));
            monthly = monthly.add(mo);
            rows.add(List.of((String) r.get("name"), ((String) r.get("kind")).toLowerCase(), ((String) r.get("frequency")).toLowerCase(), m((BigDecimal) r.get("amount")), m(mo), m(mo.multiply(BigDecimal.valueOf(12)))));
        }
        rows.add(List.of("Total", "", "", "", m(monthly), m(monthly.multiply(BigDecimal.valueOf(12)))));
        return new ReportTable("subscription-cost", "Subscription & bill cost analysis", cur, null, null, List.of("Item", "Kind", "Repeats", "Amount", "Per month", "Per year"), rows,
                List.of("Active items only. Paused, cancelled and needs-review items are excluded."));
    }

    private ReportTable budgetVariance(UUID u, String cur) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select b.name, b.period_type, b.start_date, b.end_date, b.limit_amount, b.category_id from budgets b where b.user_id = ? and b.currency = ? and b.archived_at is null and b.household_id is null", u, cur)) {
            boolean monthly = "MONTHLY".equals(r.get("period_type"));
            LocalDate s = monthly ? today.withDayOfMonth(1) : ((java.sql.Date) r.get("start_date")).toLocalDate();
            LocalDate e = monthly ? today.withDayOfMonth(today.lengthOfMonth()) : ((java.sql.Date) r.get("end_date")).toLocalDate();
            UUID cat = (UUID) r.get("category_id");
            String sql = "select coalesce(sum(-e.amount), 0) from ledger_entries e join transactions t on t.id = e.transaction_id where " + FLOW_WHERE + " and t.type = 'EXPENSE'" + (cat == null ? "" : " and t.category_id = ?");
            BigDecimal spent = cat == null ? jdbc.queryForObject(sql, BigDecimal.class, u, cur, s, e) : jdbc.queryForObject(sql, BigDecimal.class, u, cur, s, e, cat);
            BigDecimal limit = (BigDecimal) r.get("limit_amount");
            rows.add(List.of((String) r.get("name"), s + " → " + e, m(limit), m(spent), m(limit.subtract(spent)), spent.compareTo(limit) > 0 ? "Over budget" : "Within budget"));
        }
        return new ReportTable("budget-variance", "Budget variance (actual vs planned)", cur, null, null, List.of("Budget", "Period", "Planned", "Actual", "Variance", "Status"), rows,
                List.of("Variance = planned − actual. Rollover amounts are shown on the Budgets page and not included here."));
    }

    /** A PROJECTION from stated assumptions - not a prediction. Nothing here is written to the ledger. */
    private ReportTable forecast(UUID u, String cur, int horizon) {
        int days = horizon <= 30 ? 30 : horizon <= 60 ? 60 : 90;
        LocalDate today = LocalDate.now(ZoneOffset.UTC), past = today.minusDays(90);
        BigDecimal balance = nz(jdbc.queryForObject("select coalesce(sum(current_balance), 0) from accounts where user_id = ? and currency = ? and archived_at is null", BigDecimal.class, u, cur));
        BigDecimal income = nz(jdbc.queryForObject("select coalesce(sum(e.amount), 0) from ledger_entries e join transactions t on t.id = e.transaction_id where " + FLOW_WHERE + " and t.type = 'INCOME'", BigDecimal.class, u, cur, past, today));
        BigDecimal nonBillSpend = nz(jdbc.queryForObject("select coalesce(sum(-e.amount), 0) from ledger_entries e join transactions t on t.id = e.transaction_id where " + FLOW_WHERE
                + " and t.type = 'EXPENSE' and coalesce(t.payment_method, '') <> 'bill'", BigDecimal.class, u, cur, past, today));
        BigDecimal dailyNet = income.subtract(nonBillSpend).divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP);

        Map<LocalDate, BigDecimal> billsByDay = new TreeMap<>();
        jdbc.query("select amount, frequency, next_due_date from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null", rs -> {
            LocalDate d = rs.getObject("next_due_date", LocalDate.class);
            for (int g = 0; g < 400 && !d.isAfter(today.plusDays(days)); g++) {
                if (!d.isBefore(today)) billsByDay.merge(d, rs.getBigDecimal("amount"), BigDecimal::add);
                d = com.flowpilot.planning.BillMath.advance(d, rs.getString("frequency"));
            }
        }, u, cur);

        List<List<String>> rows = new ArrayList<>();
        BigDecimal running = balance, lowest = balance, weekBills = BigDecimal.ZERO;
        for (int i = 1; i <= days; i++) {
            LocalDate d = today.plusDays(i);
            BigDecimal bills = billsByDay.getOrDefault(d, BigDecimal.ZERO);
            running = running.add(dailyNet).subtract(bills);
            weekBills = weekBills.add(bills);
            lowest = lowest.min(running);
            if (i % 7 == 0 || i == days) { rows.add(List.of(d.toString(), m(weekBills), m(running))); weekBills = BigDecimal.ZERO; }
        }
        List<String> notes = List.of("PROJECTION, not a prediction. Starting balance = today's total of your " + cur + " accounts: " + m(balance) + ".",
                "Assumed average daily net from the last 90 days, excluding bill payments: " + m(dailyNet) + " per day.",
                "Scheduled bills and subscriptions (active) are subtracted on their due dates.",
                "Lowest projected balance in the period: " + m(lowest) + (lowest.signum() < 0 ? " — projected to go negative." : "."),
                "Nothing here changes your balances or is written to your ledger.");
        return new ReportTable("cash-flow-forecast", "Cash-flow forecast (" + days + " days)", cur, today, today.plusDays(days), List.of("Week ending", "Bills due that week", "Projected balance"), rows, notes);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String pct(BigDecimal part, BigDecimal whole) { return whole.signum() == 0 ? "0.0%" : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP) + "%"; }
    static YearMonth unused() { return YearMonth.now(); }
}
