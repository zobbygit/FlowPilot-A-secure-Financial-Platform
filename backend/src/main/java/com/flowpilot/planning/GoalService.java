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
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A PLANNED allocation is an intention (no ledger movement). A TRANSFER is a real internal transfer
 * between two of the user's accounts recorded in the ledger. Progress ("saved") counts transfers only.
 */
@Service
public class GoalService {
    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    private final FinancialAudit fin;

    public GoalService(JdbcTemplate jdbc, LedgerService ledger, FinancialAudit fin) {
        this.fin = fin;
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    public List<GoalView> list(UUID userId) {
        return query(userId, null);
    }

    @Transactional
    public GoalView create(UUID userId, GoalRequest r) {
        if ((r.contributionAmount() == null) != (r.contributionFrequency() == null)) {
            throw ApiException.badRequest("INVALID_SCHEDULE", "Provide both a contribution amount and a frequency, or neither.");
        }
        if (r.targetDate() != null && !r.targetDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw ApiException.badRequest("INVALID_DATE", "The target date must be in the future.");
        }
        UUID id = jdbc.queryForObject("insert into savings_goals (user_id, name, goal_type, target_amount, target_date, currency, contribution_amount, contribution_frequency) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id", UUID.class, userId, r.name().trim(), r.goalType(), r.targetAmount(), r.targetDate(),
                r.currency(), r.contributionAmount(), r.contributionFrequency());
        fin.record(userId, userId, "GOAL_CREATED", "GOAL", id, r.name().trim() + " target " + r.targetAmount().toPlainString() + " " + r.currency(), null, "ACTIVE");
        return query(userId, id).get(0);
    }

    @Transactional
    public GoalView allocate(UUID userId, UUID goalId, BigDecimal amount) {
        requireActive(userId, goalId);
        jdbc.update("insert into goal_contributions (goal_id, user_id, kind, amount) values (?, ?, 'PLANNED', ?)", goalId, userId, amount);
        fin.record(userId, userId, "GOAL_ALLOCATION_PLANNED", "GOAL", goalId, "Planned (no money moved)", null, amount.toPlainString());
        return query(userId, goalId).get(0);
    }

    @Transactional
    public GoalView transfer(UUID userId, UUID goalId, GoalTransferRequest r) {
        requireActive(userId, goalId);
        String goalCurrency = jdbc.queryForObject("select currency from savings_goals where id = ? and user_id = ?", String.class, goalId, userId);
        String toCurrency = jdbc.query("select currency from accounts where id = ? and user_id = ? and archived_at is null", (rs, i) -> rs.getString(1), r.toAccountId(), userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Account not found."));
        if (!toCurrency.equals(goalCurrency)) throw ApiException.badRequest("CURRENCY_MISMATCH", "The savings account must use the goal's currency.");

        TransactionView tx = ledger.post(userId, new PostTransactionRequest(TransactionType.TRANSFER, r.amount(), LocalDate.now(ZoneOffset.UTC),
                r.fromAccountId(), r.toAccountId(), null, "Savings goal transfer", null, null, null), null);
        jdbc.update("insert into goal_contributions (goal_id, user_id, kind, amount, transaction_id) values (?, ?, 'TRANSFERRED', ?, ?)", goalId, userId, r.amount(), tx.id());
        fin.record(userId, userId, "GOAL_TRANSFER", "GOAL", goalId, "Transferred via " + tx.id(), null, r.amount().toPlainString());
        jdbc.update("update savings_goals set status = 'COMPLETED' where id = ? and user_id = ? and status = 'ACTIVE' and "
                + "(select coalesce(sum(amount), 0) from goal_contributions where goal_id = ? and kind = 'TRANSFERRED') >= target_amount", goalId, userId, goalId);
        return query(userId, goalId).get(0);
    }

    @Transactional
    public void archive(UUID userId, UUID goalId) {
        if (jdbc.update("update savings_goals set status = 'ARCHIVED' where id = ? and user_id = ? and status <> 'ARCHIVED'", goalId, userId) == 0) {
            throw ApiException.notFound("Goal not found.");
        }
    }

    private void requireActive(UUID userId, UUID goalId) {
        String status = jdbc.query("select status from savings_goals where id = ? and user_id = ? for update", (rs, i) -> rs.getString(1), goalId, userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Goal not found."));
        if (!status.equals("ACTIVE")) throw new ApiException(HttpStatus.CONFLICT, "GOAL_NOT_ACTIVE", "This goal is no longer active.");
    }

    private List<GoalView> query(UUID userId, UUID onlyId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String sql = "select g.*, "
                + "(select coalesce(sum(c.amount), 0) from goal_contributions c where c.goal_id = g.id and c.kind = 'TRANSFERRED') as saved, "
                + "(select coalesce(sum(c.amount), 0) from goal_contributions c where c.goal_id = g.id and c.kind = 'PLANNED') as planned "
                + "from savings_goals g where g.user_id = ? and g.status <> 'ARCHIVED'" + (onlyId == null ? "" : " and g.id = ?") + " order by g.created_at";
        Object[] args = onlyId == null ? new Object[]{userId} : new Object[]{userId, onlyId};
        return jdbc.query(sql, (rs, i) -> {
            BigDecimal target = rs.getBigDecimal("target_amount"), saved = rs.getBigDecimal("saved");
            BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
            double pct = Math.min(100, saved.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP).doubleValue());
            LocalDate date = rs.getObject("target_date", LocalDate.class);
            String suggested = null;
            if (date != null && remaining.signum() > 0 && date.isAfter(today)) {
                long months = Math.max(1, ChronoUnit.MONTHS.between(today, date));
                suggested = remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING).toPlainString();
            }
            BigDecimal ca = rs.getBigDecimal("contribution_amount");
            return new GoalView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("goal_type"), target.toPlainString(), date, rs.getString("currency"),
                    ca == null ? null : ca.toPlainString(), rs.getString("contribution_frequency"), rs.getString("status"), saved.toPlainString(),
                    rs.getBigDecimal("planned").toPlainString(), pct, remaining.toPlainString(), suggested);
        }, args);
    }
}
