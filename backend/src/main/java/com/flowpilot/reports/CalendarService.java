package com.flowpilot.reports;

import com.flowpilot.common.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** PLANNED events come from schedules; COMPLETED events come from the real ledger. The two are never mixed up. */
@Service
public class CalendarService {
    public record Event(LocalDate date, String kind, String title, String amount, String currency, String state, String link) {}

    private final JdbcTemplate jdbc;

    public CalendarService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Event> events(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) throw ApiException.badRequest("INVALID_RANGE", "Choose a range of at most 92 days.");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Event> out = new ArrayList<>();

        jdbc.query("select name, kind, amount, currency, frequency, next_due_date from bills where user_id = ? and status = 'ACTIVE' and archived_at is null", rs -> {
            LocalDate d = rs.getObject("next_due_date", LocalDate.class);
            String freq = rs.getString("frequency");
            for (int guard = 0; guard < 400 && !d.isAfter(to); guard++) {
                if (!d.isBefore(from) && !d.isBefore(today)) {
                    out.add(new Event(d, rs.getString("kind"), rs.getString("name"), rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), "PLANNED", "/app/bills"));
                }
                d = switch (freq) { case "WEEKLY" -> d.plusWeeks(1); case "QUARTERLY" -> d.plusMonths(3); case "YEARLY" -> d.plusYears(1); default -> d.plusMonths(1); };
            }
        }, userId);

        jdbc.query("select name, currency, contribution_amount, contribution_frequency, created_at::date as start from savings_goals "
                + "where user_id = ? and status = 'ACTIVE' and contribution_amount is not null", rs -> {
            LocalDate d = rs.getObject("start", LocalDate.class);
            boolean weekly = "WEEKLY".equals(rs.getString("contribution_frequency"));
            for (int guard = 0; guard < 400 && !d.isAfter(to); guard++) {
                if (!d.isBefore(from) && !d.isBefore(today)) {
                    out.add(new Event(d, "SAVINGS_PLAN", "Planned contribution: " + rs.getString("name"), rs.getBigDecimal("contribution_amount").toPlainString(), rs.getString("currency"), "PLANNED", "/app/savings"));
                }
                d = weekly ? d.plusWeeks(1) : d.plusMonths(1);
            }
        }, userId);

        jdbc.query("select t.type, coalesce(t.merchant, t.description, lower(t.type)) as title, t.occurred_on, "
                + "coalesce(sum(case when t.type = 'TRANSFER' then greatest(e.amount, 0) else e.amount end), 0) as amount, min(e.currency) as currency "
                + "from transactions t join ledger_entries e on e.transaction_id = t.id where t.user_id = ? and t.occurred_on between ? and ? "
                + "and t.archived_at is null and t.reverses_transaction_id is null and t.status = 'POSTED' and t.type <> 'OPENING_BALANCE' "
                + "group by t.id, t.type, title, t.occurred_on order by t.occurred_on desc limit 300", rs -> {
            out.add(new Event(rs.getObject("occurred_on", LocalDate.class), rs.getString("type"), rs.getString("title"), rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), "COMPLETED", "/app/transactions"));
        }, userId, from, to);

        out.sort(Comparator.comparing(Event::date).thenComparing(Event::state));
        return out;
    }
}
