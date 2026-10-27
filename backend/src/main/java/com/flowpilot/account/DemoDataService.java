package com.flowpilot.account;

import com.flowpilot.common.ApiException;
import com.flowpilot.files.MemoService;
import com.flowpilot.finance.AccountService;
import com.flowpilot.finance.AccountType;
import com.flowpilot.finance.FinanceDtos.CreateAccountRequest;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.planning.BillService;
import com.flowpilot.planning.BudgetService;
import com.flowpilot.planning.GoalService;
import com.flowpilot.planning.PlanningDtos.BillRequest;
import com.flowpilot.planning.PlanningDtos.BudgetRequest;
import com.flowpilot.planning.PlanningDtos.GoalRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional sample data so a new user can explore the app. It goes through the SAME services as real input (real ledger entries,
 * real balances), only for a user with no accounts yet, and every account is named "(demo)" so it can be archived afterwards.
 */
@Service
public class DemoDataService {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final LedgerService ledger;
    private final BillService bills;
    private final BudgetService budgets;
    private final GoalService goals;
    private final MemoService memos;

    public DemoDataService(JdbcTemplate jdbc, AccountService accounts, LedgerService ledger, BillService bills, BudgetService budgets, GoalService goals, MemoService memos) {
        this.jdbc = jdbc; this.accounts = accounts; this.ledger = ledger; this.bills = bills; this.budgets = budgets; this.goals = goals; this.memos = memos;
    }

    private record Tx(int daysAgo, String type, String amount, String category, String merchant) {}

    @Transactional
    public int seed(UUID u) {
        if (jdbc.queryForObject("select count(*) from accounts where user_id = ?", Integer.class, u) > 0) throw new ApiException(HttpStatus.CONFLICT, "NOT_EMPTY", "Demo data can only be added to a workspace with no accounts.");
        String cur = jdbc.queryForObject("select preferred_currency from user_profiles where user_id = ?", String.class, u);
        UUID checking = accounts.create(u, new CreateAccountRequest("Checking (demo)", AccountType.BANK, cur, new BigDecimal("3200"), false)).id();
        accounts.create(u, new CreateAccountRequest("Savings (demo)", AccountType.SAVINGS, cur, new BigDecimal("5000"), false));
        UUID cash = accounts.create(u, new CreateAccountRequest("Cash (demo)", AccountType.CASH, cur, new BigDecimal("150"), false)).id();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Tx> txs = List.of(new Tx(55, "INCOME", "3500", "Salary", "Acme Ltd"), new Tx(25, "INCOME", "3500", "Salary", "Acme Ltd"), new Tx(40, "INCOME", "220", "Freelance", "Side project"),
                new Tx(52, "EXPENSE", "86.40", "Food & Dining", "Fresh Market"), new Tx(48, "EXPENSE", "12.50", "Food & Dining", "Corner Cafe"), new Tx(45, "EXPENSE", "38.00", "Transport", "City Transit"),
                new Tx(41, "EXPENSE", "64.99", "Shopping", "Home Store"), new Tx(36, "EXPENSE", "23.00", "Entertainment", "Cinema"), new Tx(33, "EXPENSE", "92.10", "Food & Dining", "Fresh Market"),
                new Tx(30, "EXPENSE", "15.99", "Bills & Utilities", "StreamCo"), new Tx(28, "EXPENSE", "900", "Bills & Utilities", "Landlord"), new Tx(22, "EXPENSE", "41.30", "Transport", "Fuel Stop"),
                new Tx(19, "EXPENSE", "58.75", "Shopping", "Bookshop"), new Tx(15, "EXPENSE", "74.20", "Food & Dining", "Fresh Market"), new Tx(12, "EXPENSE", "29.00", "Health", "Pharmacy"),
                new Tx(9, "EXPENSE", "15.99", "Bills & Utilities", "StreamCo"), new Tx(8, "EXPENSE", "900", "Bills & Utilities", "Landlord"), new Tx(5, "EXPENSE", "33.60", "Food & Dining", "Corner Cafe"),
                new Tx(3, "EXPENSE", "46.00", "Entertainment", "Concert Tickets"), new Tx(1, "EXPENSE", "27.80", "Food & Dining", "Fresh Market"));
        int n = 0;
        for (Tx t : txs) {
            UUID cat = jdbc.query("select id from categories where user_id is null and name = ? and kind = ?", (rs, i) -> rs.getObject(1, UUID.class), t.category(), t.type()).stream().findFirst().orElse(null);
            ledger.post(u, new PostTransactionRequest(TransactionType.valueOf(t.type()), new BigDecimal(t.amount()), today.minusDays(t.daysAgo()), checking, null, cat, t.category() + " (demo)", t.merchant(), "card", null), null);
            n++;
        }
        ledger.post(u, new PostTransactionRequest(TransactionType.EXPENSE, new BigDecimal("18.00"), today.minusDays(2), cash, null, null, "Market stall (demo)", "Local market", "cash", null), null);
        bills.create(u, new BillRequest("StreamCo (demo)", "SUBSCRIPTION", new BigDecimal("15.99"), cur, "MONTHLY", today.plusDays(6), null, checking, 3, "Sample subscription"));
        bills.create(u, new BillRequest("Rent (demo)", "BILL", new BigDecimal("900"), cur, "MONTHLY", today.plusDays(3), null, checking, 5, "Sample bill"));
        UUID food = jdbc.query("select id from categories where user_id is null and name = 'Food & Dining'", (rs, i) -> rs.getObject(1, UUID.class), new Object[0]).stream().findFirst().orElse(null);
        budgets.create(u, new BudgetRequest("Food & dining (demo)", "MONTHLY", null, null, food, new BigDecimal("400"), cur, false, List.of(80, 100)));
        goals.create(u, new GoalRequest("Emergency fund (demo)", "EMERGENCY", new BigDecimal("6000"), today.plusMonths(8), cur, null, null));
        memos.create(u, new MemoService.MemoRequest("Welcome to the demo workspace", "These sample records are real entries in your ledger. Archive the “(demo)” accounts when you're done exploring.", "demo", true, null, null, null));
        return n + 1;
    }
}
