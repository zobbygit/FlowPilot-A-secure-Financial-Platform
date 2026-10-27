package com.flowpilot.sandbox;

import com.flowpilot.common.ApiException;
import com.flowpilot.planning.BillMath;
import com.flowpilot.sandbox.SandboxEngine.Event;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SandboxService {
    public record EventDef(@NotBlank @Pattern(regexp = "ONE_OFF|MONTHLY_CHANGE|CANCEL_BILL") String kind, @NotBlank @Size(max = 80) String label, LocalDate date,
                           @Digits(integer = 15, fraction = 4) BigDecimal amount, UUID billId) {}
    public record SimulateRequest(@NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency, @Min(30) @Max(90) int horizonDays, @Size(max = 12) List<@Valid EventDef> events) {}
    public record ScenarioRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency, @Min(30) @Max(90) int horizonDays, @NotNull @Size(max = 12) List<@Valid EventDef> events) {}
    public record ScenarioView(UUID id, String name, String currency, int horizonDays, List<EventDef> events) {}
    public record Assumptions(String startingBalance, String averageDailyNet, int scheduledBills, List<String> notes) {}
    public record Simulation(String currency, int horizonDays, Assumptions assumptions, SandboxEngine.Result result, String disclaimer) {}

    private final JdbcTemplate jdbc;

    public SandboxService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Reads real data, writes nothing. */
    @Transactional(readOnly = true)
    public Simulation simulate(UUID u, SimulateRequest r) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<EventDef> defs = r.events() == null ? List.of() : r.events();
        for (EventDef e : defs) check(u, e, today, r.horizonDays());
        BigDecimal balance = nz(jdbc.queryForObject("select coalesce(sum(current_balance), 0) from accounts where user_id = ? and currency = ? and archived_at is null", BigDecimal.class, u, r.currency()));
        String flow = "from ledger_entries e join transactions t on t.id = e.transaction_id where e.user_id = ? and e.currency = ? and t.status = 'POSTED' and t.reverses_transaction_id is null and t.archived_at is null and t.occurred_on between ? and ?";
        LocalDate past = today.minusDays(90);
        BigDecimal income = nz(jdbc.queryForObject("select coalesce(sum(e.amount), 0) " + flow + " and t.type = 'INCOME'", BigDecimal.class, u, r.currency(), past, today));
        BigDecimal spend = nz(jdbc.queryForObject("select coalesce(sum(-e.amount), 0) " + flow + " and t.type = 'EXPENSE' and coalesce(t.payment_method, '') <> 'bill'", BigDecimal.class, u, r.currency(), past, today));
        BigDecimal dailyNet = income.subtract(spend).divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP);

        Map<LocalDate, List<Map.Entry<UUID, BigDecimal>>> bills = new TreeMap<>();
        int[] count = {0};
        jdbc.query("select id, amount, frequency, next_due_date from bills where user_id = ? and currency = ? and status = 'ACTIVE' and archived_at is null", rs -> {
            LocalDate d = rs.getObject("next_due_date", LocalDate.class);
            for (int g = 0; g < 400 && !d.isAfter(today.plusDays(r.horizonDays())); g++) {
                if (d.isAfter(today)) { bills.computeIfAbsent(d, k -> new ArrayList<>()).add(Map.entry(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"))); count[0]++; }
                d = BillMath.advance(d, rs.getString("frequency"));
            }
        }, u, r.currency());

        List<Event> events = defs.stream().map(e -> new Event(e.kind(), e.label(), e.date(), e.amount(), e.billId())).toList();
        var result = SandboxEngine.simulate(balance, dailyNet, bills, events, today, r.horizonDays());
        List<String> notes = List.of("HYPOTHETICAL PROJECTION — not a prediction and not advice. Nothing here changes your balances or is written to your ledger.",
                "Starting point: today's total of your active " + r.currency() + " accounts.", "Daily net is your 90-day average income minus non-bill spending; it is held constant.",
                "Active bills and subscriptions are subtracted on their due dates. “Cancel bill” events remove a bill from the scenario line only.",
                "Your scenario events are applied exactly as entered, on the dates given.");
        return new Simulation(r.currency(), r.horizonDays(), new Assumptions(balance.setScale(2, RoundingMode.HALF_UP).toPlainString(), dailyNet.setScale(2, RoundingMode.HALF_UP).toPlainString(), count[0], notes), result,
                "Projected from the assumptions above. Real results will differ.");
    }

    public List<ScenarioView> scenarios(UUID u) {
        return jdbc.query("select * from sandbox_scenarios where user_id = ? order by created_at desc limit 50", (rs, i) -> view(rs), u);
    }

    @Transactional
    public ScenarioView save(UUID u, ScenarioRequest r) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (EventDef e : r.events()) check(u, e, today, r.horizonDays());
        if (jdbc.queryForObject("select count(*) from sandbox_scenarios where user_id = ?", Integer.class, u) >= 50) throw ApiException.badRequest("TOO_MANY_SCENARIOS", "Delete an old scenario before saving a new one.");
        UUID id = jdbc.queryForObject("insert into sandbox_scenarios (user_id, name, currency, horizon_days) values (?, ?, ?, ?) returning id", UUID.class, u, r.name().trim(), r.currency(), r.horizonDays());
        int pos = 1;
        for (EventDef e : r.events()) jdbc.update("insert into sandbox_events (scenario_id, position, kind, label, event_date, amount, bill_id) values (?, ?, ?, ?, ?, ?, ?)", id, pos++, e.kind(), e.label().trim(), e.date(), e.amount(), e.billId());
        return jdbc.query("select * from sandbox_scenarios where id = ?", (rs, i) -> view(rs), id).get(0);
    }

    @Transactional
    public void delete(UUID u, UUID id) {
        if (jdbc.update("delete from sandbox_scenarios where id = ? and user_id = ?", id, u) == 0) throw ApiException.notFound("Scenario not found.");
    }

    private void check(UUID u, EventDef e, LocalDate today, int horizon) {
        if (e.kind().equals("CANCEL_BILL")) {
            if (e.billId() == null || jdbc.queryForObject("select count(*) from bills where id = ? and user_id = ?", Integer.class, e.billId(), u) == 0) throw ApiException.badRequest("INVALID_BILL", "Choose one of your bills to cancel in this scenario.");
            return;
        }
        if (e.date() == null || e.amount() == null || e.amount().signum() == 0) throw ApiException.badRequest("INVALID_EVENT", "“" + e.label() + "” needs a date and a non-zero amount.");
        if (e.date().isBefore(today) || e.date().isAfter(today.plusDays(horizon))) throw ApiException.badRequest("INVALID_EVENT_DATE", "“" + e.label() + "” must fall within the next " + horizon + " days.");
    }

    private ScenarioView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<EventDef> ev = jdbc.query("select kind, label, event_date, amount, bill_id from sandbox_events where scenario_id = ? order by position",
                (r, i) -> new EventDef(r.getString(1), r.getString(2), r.getObject(3, LocalDate.class), r.getBigDecimal(4), r.getObject(5, UUID.class)), id);
        return new ScenarioView(id, rs.getString("name"), rs.getString("currency"), rs.getInt("horizon_days"), ev);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
