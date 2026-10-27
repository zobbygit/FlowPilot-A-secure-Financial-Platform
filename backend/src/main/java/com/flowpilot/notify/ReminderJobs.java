package com.flowpilot.notify;

import com.flowpilot.planning.BudgetService;
import com.flowpilot.planning.PlanningDtos.BudgetView;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled reminders. Every notification carries a deterministic dedupe key, so re-running a job
 * (restart, overlap, two instances) can never create a duplicate. Day boundaries use UTC.
 */
@Component
public class ReminderJobs {
    private static final Logger log = LoggerFactory.getLogger(ReminderJobs.class);
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final BudgetService budgets;

    public ReminderJobs(JdbcTemplate jdbc, NotificationService notifications, BudgetService budgets) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.budgets = budgets;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    public void run() {
        try { bills(); } catch (RuntimeException e) { log.error("Bill reminders failed", e); }
        try { budgetThresholds(); } catch (RuntimeException e) { log.error("Budget alerts failed", e); }
        try { lowBalances(); } catch (RuntimeException e) { log.error("Low-balance alerts failed", e); }
    }

    void bills() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        jdbc.query("select id, user_id, name, next_due_date, amount, currency from bills where status = 'ACTIVE' and archived_at is null "
                + "and next_due_date <= current_date + reminder_days_before", rs -> {
            UUID id = rs.getObject("id", UUID.class), user = rs.getObject("user_id", UUID.class);
            LocalDate due = rs.getObject("next_due_date", LocalDate.class);
            boolean overdue = due.isBefore(today);
            notifications.notify(user, "BILL", (overdue ? "Overdue: " : "Due soon: ") + rs.getString("name"),
                    "Due " + due + " · " + rs.getBigDecimal("amount").toPlainString() + " " + rs.getString("currency"), "/app/bills",
                    (overdue ? "bill-overdue:" : "bill:") + id + ":" + due);
        });
    }

    void budgetThresholds() {
        List<UUID> users = jdbc.queryForList("select distinct user_id from budgets where archived_at is null", UUID.class);
        for (UUID u : users) {
            for (BudgetView b : budgets.list(u)) {
                for (int t : b.crossedThresholds()) {
                    notifications.notify(u, "BUDGET", t >= 100 ? "Budget exceeded: " + b.name() : "Budget at " + t + "%: " + b.name(),
                            b.percentUsed() + "% of your limit is used.", "/app/budgets", "budget:" + b.id() + ":" + b.periodStart() + ":" + t);
                }
            }
        }
    }

    void lowBalances() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        jdbc.query("select a.id, a.user_id, a.name, a.currency, a.current_balance from accounts a "
                + "join notification_preferences p on p.user_id = a.user_id join user_profiles up on up.user_id = a.user_id "
                + "where a.archived_at is null and p.low_balance and p.low_balance_threshold is not null "
                + "and a.currency = up.preferred_currency and a.current_balance < p.low_balance_threshold", rs -> {
            notifications.notify(rs.getObject("user_id", UUID.class), "LOW_BALANCE", "Low balance: " + rs.getString("name"),
                    "Balance is " + rs.getBigDecimal("current_balance").toPlainString() + " " + rs.getString("currency"), "/app/accounts",
                    "lowbal:" + rs.getObject("id", UUID.class) + ":" + today);
        });
    }
}
