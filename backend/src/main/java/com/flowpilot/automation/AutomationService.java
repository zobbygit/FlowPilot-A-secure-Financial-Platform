package com.flowpilot.automation;

import com.flowpilot.audit.FinancialAudit;
import com.flowpilot.automation.AutomationDtos.*;
import com.flowpilot.automation.AutomationEngine.Action;
import com.flowpilot.common.ApiException;
import com.flowpilot.notify.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Rules plan allocations (intent + reservation). They never create transfers, never touch the ledger, never overspend. */
@Service
public class AutomationService {
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final TransactionTemplate newTx;

    private final FinancialAudit fin;

    public AutomationService(JdbcTemplate jdbc, NotificationService notifications, PlatformTransactionManager tm, FinancialAudit fin) {
        this.fin = fin;
        this.jdbc = jdbc; this.notifications = notifications;
        this.newTx = new TransactionTemplate(tm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ rules CRUD

    public List<RuleView> rules(UUID userId) {
        return jdbc.query("select r.*, (select max(created_at) from automation_runs x where x.rule_id = r.id) as last_at, "
                + "(select status from automation_runs x where x.rule_id = r.id order by created_at desc limit 1) as last_status from automation_rules r where r.user_id = ? and r.status <> 'ARCHIVED' order by r.priority, r.created_at",
                (rs, i) -> ruleView(rs), userId);
    }

    @Transactional
    public RuleView create(UUID userId, RuleRequest r) {
        validate(userId, r);
        UUID id = jdbc.queryForObject("insert into automation_rules (user_id, name, trigger_type, schedule, schedule_day, min_trigger_amount, trigger_category_id, source_account_id, priority, per_run_limit) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, userId, r.name().trim(), r.triggerType(), r.schedule(), r.scheduleDay(), r.minTriggerAmount(), r.triggerCategoryId(), r.sourceAccountId(), r.priority() == null ? 100 : r.priority(), r.perRunLimit());
        writeActions(id, r.actions());
        return get(userId, id);
    }

    @Transactional
    public RuleView update(UUID userId, UUID id, RuleRequest r) {
        validate(userId, r);
        if (jdbc.update("update automation_rules set name = ?, trigger_type = ?, schedule = ?, schedule_day = ?, min_trigger_amount = ?, trigger_category_id = ?, source_account_id = ?, priority = ?, per_run_limit = ? where id = ? and user_id = ? and status <> 'ARCHIVED'",
                r.name().trim(), r.triggerType(), r.schedule(), r.scheduleDay(), r.minTriggerAmount(), r.triggerCategoryId(), r.sourceAccountId(), r.priority() == null ? 100 : r.priority(), r.perRunLimit(), id, userId) == 0) throw ApiException.notFound("Rule not found.");
        jdbc.update("delete from automation_actions where rule_id = ?", id);
        writeActions(id, r.actions());
        return get(userId, id);
    }

    @Transactional
    public void setStatus(UUID userId, UUID id, String status) {
        if (jdbc.update("update automation_rules set status = ? where id = ? and user_id = ? and status <> 'ARCHIVED'", status, id, userId) == 0) throw ApiException.notFound("Rule not found.");
    }

    // ------------------------------------------------------------------ preview / run / retry

    @Transactional(readOnly = true)
    public Preview preview(UUID userId, UUID ruleId, BigDecimal income) {
        Rule rule = load(userId, ruleId);
        if (usesPercent(rule) && income == null) throw ApiException.badRequest("INCOME_REQUIRED", "Enter an example income amount to preview percentage actions.");
        BigDecimal available = available(rule.sourceAccount);
        var outcomes = AutomationEngine.compute(toActions(userId, rule), income, available, rule.perRunLimit, Set.of());
        return new Preview(income == null ? null : income.toPlainString(), available.toPlainString(), rule.perRunLimit == null ? null : rule.perRunLimit.toPlainString(), outcomes,
                AutomationEngine.total(outcomes).toPlainString(), "Preview only. Nothing was saved and no balances changed.");
    }

    @Transactional
    public RunView runManual(UUID userId, UUID ruleId, BigDecimal income) {
        Rule rule = load(userId, ruleId);
        if (!rule.status.equals("ACTIVE")) throw new ApiException(HttpStatus.CONFLICT, "RULE_NOT_ACTIVE", "Resume this rule before running it.");
        if (usesPercent(rule) && income == null) throw ApiException.badRequest("INCOME_REQUIRED", "Enter the income amount to apply percentages to.");
        UUID run = execute(rule, "manual:" + UUID.randomUUID(), income, null, 1, Set.of());
        return runView(userId, run);
    }

    @Transactional
    public RunView retry(UUID userId, UUID runId) {
        var rows = jdbc.queryForList("select rule_id, trigger_key, trigger_amount, status, attempt from automation_runs where id = ? and user_id = ?", runId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Run not found.");
        var r = rows.get(0);
        if ("SUCCESS".equals(r.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "NOT_RETRYABLE", "This run already succeeded.");
        if (jdbc.queryForObject("select count(*) from automation_runs where retry_of = ?", Integer.class, runId) > 0) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RETRIED", "This run was already retried. Retry the newest attempt.");
        Rule rule = load(userId, (UUID) r.get("rule_id"));
        int attempt = ((Number) r.get("attempt")).intValue() + 1;
        Set<String> done = new HashSet<>(jdbc.query("select target_type || ':' || target_id from automation_allocations where run_id = ? and status = 'PLANNED'", (rs, i) -> rs.getString(1), runId));
        String base = ((String) r.get("trigger_key")).replaceAll("#r\\d+$", "");
        UUID run = execute(rule, base + "#r" + attempt, (BigDecimal) r.get("trigger_amount"), runId, attempt, done);
        return runView(userId, run);
    }

    @Transactional
    public void release(UUID userId, UUID allocationId) {
        var rows = jdbc.queryForList("select goal_contribution_id from automation_allocations where id = ? and user_id = ? and status = 'PLANNED' for update", allocationId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("No open allocation found.");
        jdbc.update("update automation_allocations set status = 'RELEASED', goal_contribution_id = null where id = ?", allocationId);
        UUID gc = (UUID) rows.get(0).get("goal_contribution_id");
        if (gc != null) jdbc.update("delete from goal_contributions where id = ? and kind = 'PLANNED'", gc);   // planned intent only; never ledger data
    }

    public List<RunView> runs(UUID userId) {
        return jdbc.query("select id from automation_runs where user_id = ? order by created_at desc limit 50", (rs, i) -> rs.getObject(1, UUID.class), userId).stream().map(id -> runView(userId, id)).toList();
    }

    // ------------------------------------------------------------------ triggers

    /**
     * Runs after the income transaction has committed, in its OWN transaction. A failure here is logged and can never
     * make the user's income posting look failed (it already succeeded).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncome(IncomePosted e) {
        try { newTx.executeWithoutResult(st -> handleIncome(e)); } catch (RuntimeException ex) { log.error("Automation failed for an income event", ex); }
    }

    void handleIncome(IncomePosted e) {
        List<UUID> ids = jdbc.query("select r.id from automation_rules r join accounts a on a.id = r.source_account_id where r.user_id = ? and r.status = 'ACTIVE' and r.trigger_type = 'INCOME_RECEIVED' and a.currency = ? "
                + "and (r.min_trigger_amount is null or r.min_trigger_amount <= ?) and (r.trigger_category_id is null or r.trigger_category_id = ?) order by r.priority, r.created_at",
                (rs, i) -> rs.getObject(1, UUID.class), e.userId(), e.currency(), e.amount(), e.categoryId());
        for (UUID id : ids) execute(load(e.userId(), id), "income:" + e.transactionId(), e.amount(), null, 1, Set.of());
    }

    @Transactional
    public int runSchedules(LocalDate today) {
        List<UUID[]> due = jdbc.query("select id, user_id from automation_rules where status = 'ACTIVE' and trigger_type = 'SCHEDULE' and ((schedule = 'WEEKLY' and schedule_day = ?) or (schedule = 'MONTHLY' and least(schedule_day, ?) = ?)) order by priority",
                (rs, i) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, today.getDayOfWeek().getValue(), today.lengthOfMonth(), today.getDayOfMonth());
        int n = 0;
        for (UUID[] d : due) { if (execute(load(d[1], d[0]), "sched:" + today, null, null, 1, Set.of()) != null) n++; }
        return n;
    }

    // ------------------------------------------------------------------ core

    /** Serialised per source account (row lock), idempotent per (rule, trigger key). Returns null if this trigger already ran. */
    private UUID execute(Rule rule, String key, BigDecimal triggerAmount, UUID retryOf, int attempt, Set<String> alreadyDone) {
        jdbc.queryForList("select id from accounts where id = ? for update", rule.sourceAccount);
        if (jdbc.queryForObject("select count(*) from automation_runs where rule_id = ? and trigger_key = ?", Integer.class, rule.id, key) > 0) return null;
        BigDecimal available = available(rule.sourceAccount);
        var outcomes = AutomationEngine.compute(toActions(rule.userId, rule), triggerAmount, available, rule.perRunLimit, alreadyDone);
        String status = AutomationEngine.overallStatus(outcomes);
        BigDecimal total = AutomationEngine.total(outcomes);
        String currency = jdbc.queryForObject("select currency from accounts where id = ?", String.class, rule.sourceAccount);
        String message = status.equals("SUCCESS") ? null : outcomes.stream().filter(o -> o.status().startsWith("SKIPPED") && !o.status().equals("SKIPPED_DONE")).map(o -> o.targetName() + ": " + o.message()).collect(Collectors.joining("; "));
        UUID run = jdbc.queryForObject("insert into automation_runs (rule_id, user_id, trigger_key, trigger_amount, status, total_allocated, message, retry_of, attempt) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, rule.id, rule.userId, key, triggerAmount, status, total, message == null ? null : trunc(message, 500), retryOf, attempt);
        for (Outcome o : outcomes) {
            UUID contribution = null;
            if (o.status().equals("PLANNED") && o.targetType().equals("GOAL")) {
                contribution = jdbc.queryForObject("insert into goal_contributions (goal_id, user_id, kind, amount) values (?, ?, 'PLANNED', ?) returning id", UUID.class, o.targetId(), rule.userId, new BigDecimal(o.allocated()));
            }
            jdbc.update("insert into automation_allocations (run_id, user_id, position, target_type, target_id, target_name, requested, amount, currency, source_account_id, status, message, goal_contribution_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    run, rule.userId, o.position(), o.targetType(), o.targetId(), o.targetName(), new BigDecimal(o.requested()), new BigDecimal(o.allocated()), currency, rule.sourceAccount, o.status(), o.message(), contribution);
        }
        fin.record(rule.userId, rule.userId, "AUTOMATION_RUN", "RULE", rule.id, rule.name + ": " + status.toLowerCase() + ", planned " + total.toPlainString(), null, key.startsWith("income:") ? "income trigger" : key.startsWith("sched:") ? "schedule" : "manual/retry");
        if (!status.equals("SUCCESS")) notifications.notify(rule.userId, "AUTOMATION", "Automation “" + rule.name + "” " + (status.equals("PARTIAL") ? "partly ran" : "could not run"), message == null ? null : trunc(message, 400), "/app/automation", "auto:" + run);
        return run;
    }

    /** Unallocated = account balance minus everything already reserved by open planned allocations from that account. */
    private BigDecimal available(UUID account) {
        BigDecimal bal = jdbc.queryForObject("select current_balance from accounts where id = ?", BigDecimal.class, account);
        BigDecimal reserved = jdbc.queryForObject("select coalesce(sum(amount), 0) from automation_allocations where source_account_id = ? and status = 'PLANNED'", BigDecimal.class, account);
        return bal.subtract(reserved).max(BigDecimal.ZERO);
    }

    private List<Action> toActions(UUID userId, Rule rule) {
        String currency = jdbc.queryForObject("select currency from accounts where id = ?", String.class, rule.sourceAccount);
        List<Action> out = new ArrayList<>();
        jdbc.query("select position, target_type, target_id, amount_type, amount_value from automation_actions where rule_id = ? order by position", rs -> {
            String type = rs.getString("target_type"); UUID target = rs.getObject("target_id", UUID.class);
            String sql = switch (type) {
                case "GOAL" -> "select name, currency, status = 'ACTIVE' as ok, 'This goal is no longer active.' as why from savings_goals where id = ? and user_id = ?";
                case "BILL" -> "select name, currency, (status in ('ACTIVE', 'NEEDS_REVIEW') and archived_at is null) as ok, 'That bill is paused, cancelled or removed.' as why from bills where id = ? and user_id = ?";
                default -> "select name, currency, archived_at is null as ok, 'That budget was archived.' as why from budgets where id = ? and user_id = ?";
            };
            var t = jdbc.queryForList(sql, target, userId);
            String name = t.isEmpty() ? "Missing target" : (String) t.get(0).get("name");
            boolean ok = !t.isEmpty() && Boolean.TRUE.equals(t.get(0).get("ok")) && currency.equals(t.get(0).get("currency"));
            String why = t.isEmpty() ? "That item no longer exists." : !currency.equals(t.get(0).get("currency")) ? "Its currency differs from the source account." : (String) t.get(0).get("why");
            out.add(new Action(rs.getInt("position"), type, target, name, rs.getString("amount_type"), rs.getBigDecimal("amount_value"), ok, why));
        }, rule.id);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private record Rule(UUID id, UUID userId, String name, String status, String triggerType, UUID sourceAccount, BigDecimal perRunLimit) {}

    private Rule load(UUID userId, UUID id) {
        return jdbc.query("select id, user_id, name, status, trigger_type, source_account_id, per_run_limit from automation_rules where id = ? and user_id = ? and status <> 'ARCHIVED'",
                (rs, i) -> new Rule(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getBigDecimal(7)), id, userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Rule not found."));
    }

    private boolean usesPercent(Rule rule) {
        return jdbc.queryForObject("select count(*) from automation_actions where rule_id = ? and amount_type = 'PERCENT'", Integer.class, rule.id) > 0;
    }

    private void validate(UUID userId, RuleRequest r) {
        if (r.triggerType().equals("SCHEDULE") && (r.schedule() == null || r.scheduleDay() == null)) throw ApiException.badRequest("SCHEDULE_REQUIRED", "A scheduled rule needs a frequency and a day.");
        if (!r.triggerType().equals("SCHEDULE") && (r.schedule() != null || r.scheduleDay() != null)) throw ApiException.badRequest("SCHEDULE_NOT_ALLOWED", "Only scheduled rules take a schedule.");
        if (r.schedule() != null && r.schedule().equals("WEEKLY") && r.scheduleDay() > 7) throw ApiException.badRequest("INVALID_DAY", "Weekly rules use a weekday from 1 (Monday) to 7 (Sunday).");
        if (r.triggerType().equals("SCHEDULE") && r.actions().stream().anyMatch(a -> a.amountType().equals("PERCENT"))) throw ApiException.badRequest("PERCENT_NEEDS_INCOME", "Percentages need an income amount, so they can't be used with a schedule.");
        if (!r.triggerType().equals("INCOME_RECEIVED") && (r.minTriggerAmount() != null || r.triggerCategoryId() != null)) throw ApiException.badRequest("CONDITIONS_NOT_ALLOWED", "Income conditions only apply to income-triggered rules.");
        if (jdbc.queryForObject("select count(*) from accounts where id = ? and user_id = ? and archived_at is null", Integer.class, r.sourceAccountId(), userId) == 0) throw ApiException.notFound("Source account not found.");
        if (r.triggerCategoryId() != null && jdbc.queryForObject("select count(*) from categories where id = ? and kind = 'INCOME' and (user_id is null or user_id = ?)", Integer.class, r.triggerCategoryId(), userId) == 0) throw ApiException.badRequest("INVALID_CATEGORY", "Choose an income category.");
        Set<String> seen = new HashSet<>();
        for (ActionDef a : r.actions()) {
            if (!seen.add(a.targetType() + ":" + a.targetId())) throw ApiException.badRequest("DUPLICATE_TARGET", "Each target can appear only once in a rule.");
            if (a.amountType().equals("PERCENT") && a.amountValue().compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("INVALID_PERCENT", "A percentage can't exceed 100.");
            String table = switch (a.targetType()) { case "GOAL" -> "savings_goals"; case "BILL" -> "bills"; default -> "budgets"; };
            if (jdbc.queryForObject("select count(*) from " + table + " where id = ? and user_id = ?", Integer.class, a.targetId(), userId) == 0) throw ApiException.notFound("One of the chosen targets was not found.");
        }
        long percentSum = r.actions().stream().filter(a -> a.amountType().equals("PERCENT")).map(a -> a.amountValue()).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
        if (percentSum > 100) throw ApiException.badRequest("PERCENT_TOTAL", "Percentages in one rule can't add up to more than 100.");
    }

    private void writeActions(UUID ruleId, List<ActionDef> actions) {
        int pos = 1;
        for (ActionDef a : actions) jdbc.update("insert into automation_actions (rule_id, position, target_type, target_id, amount_type, amount_value) values (?, ?, ?, ?, ?, ?)", ruleId, pos++, a.targetType(), a.targetId(), a.amountType(), a.amountValue());
    }

    private RuleView get(UUID userId, UUID id) { return rules(userId).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("Rule not found.")); }

    private RuleView ruleView(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<ActionDef> actions = jdbc.query("select target_type, target_id, amount_type, amount_value from automation_actions where rule_id = ? order by position",
                (r, i) -> new ActionDef(r.getString(1), r.getObject(2, UUID.class), r.getString(3), r.getBigDecimal(4)), id);
        java.sql.Timestamp last = rs.getTimestamp("last_at");
        BigDecimal min = rs.getBigDecimal("min_trigger_amount"), limit = rs.getBigDecimal("per_run_limit");
        return new RuleView(id, rs.getString("name"), rs.getString("status"), rs.getString("trigger_type"), rs.getString("schedule"), (Integer) rs.getObject("schedule_day"), min == null ? null : min.toPlainString(),
                rs.getObject("trigger_category_id", UUID.class), rs.getObject("source_account_id", UUID.class), rs.getInt("priority"), limit == null ? null : limit.toPlainString(), actions, last == null ? null : last.toInstant(), rs.getString("last_status"));
    }

    private RunView runView(UUID userId, UUID runId) {
        return jdbc.query("select r.*, ru.name as rule_name, (select count(*) from automation_runs c where c.retry_of = r.id) as retried from automation_runs r join automation_rules ru on ru.id = r.rule_id where r.id = ? and r.user_id = ?", (rs, i) -> {
            List<Outcome> outcomes = jdbc.query("select position, target_type, target_id, target_name, requested, amount, status, message from automation_allocations where run_id = ? order by position",
                    (a, j) -> new Outcome(a.getInt(1), a.getString(2), a.getObject(3, UUID.class), a.getString(4), a.getBigDecimal(5).toPlainString(), a.getBigDecimal(6).toPlainString(), a.getString(7), a.getString(8)), runId);
            BigDecimal ta = rs.getBigDecimal("trigger_amount");
            String status = rs.getString("status");
            return new RunView(runId, rs.getObject("rule_id", UUID.class), rs.getString("rule_name"), status, rs.getString("trigger_key"), ta == null ? null : ta.toPlainString(), rs.getBigDecimal("total_allocated").toPlainString(),
                    rs.getString("message"), rs.getInt("attempt"), rs.getObject("retry_of", UUID.class), rs.getTimestamp("created_at").toInstant(), outcomes, !status.equals("SUCCESS") && rs.getInt("retried") == 0);
        }, runId, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Run not found."));
    }

    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
    static Instant unused() { return Instant.now(); }
    static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }
}
