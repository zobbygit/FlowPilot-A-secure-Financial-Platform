package com.flowpilot.finance;

import com.flowpilot.common.ApiException;
import com.flowpilot.domain.UserProfile;
import com.flowpilot.finance.FinanceDtos.*;
import com.flowpilot.repo.UserProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregation straight from the ledger: dashboard numbers always agree with the accounts. */
@Service
public class DashboardService {
    private final LedgerEntryRepository ledger;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final FinanceTransactionRepository transactions;
    private final LedgerService ledgerService;
    private final UserProfileRepository profiles;

    public DashboardService(LedgerEntryRepository ledger, AccountRepository accounts, CategoryRepository categories,
                            FinanceTransactionRepository transactions, LedgerService ledgerService, UserProfileRepository profiles) {
        this.ledger = ledger; this.accounts = accounts; this.categories = categories;
        this.transactions = transactions; this.ledgerService = ledgerService; this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public DashboardView summary(UUID userId, LocalDate from, LocalDate to, String currencyParam) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (from == null || to == null) { from = today.withDayOfMonth(1); to = today; }
        if (to.isBefore(from)) throw ApiException.badRequest("INVALID_RANGE", "The end date must not be before the start date.");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366) throw ApiException.badRequest("RANGE_TOO_LARGE", "Choose a range of at most one year.");

        String currency = currencyParam != null && currencyParam.matches("[A-Z]{3}") ? currencyParam
                : profiles.findById(userId).map(UserProfile::getPreferredCurrency).orElse("USD");

        List<Account> all = accounts.findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId);
        BigDecimal total = all.stream().filter(a -> a.getCurrency().equals(currency))
                .map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LedgerRow> cur = ledger.flows(userId, currency, from, to);
        LocalDate prevTo = from.minusDays(1), prevFrom = prevTo.minusDays(days - 1);
        List<LedgerRow> prev = ledger.flows(userId, currency, prevFrom, prevTo);
        BigDecimal inc = income(cur), exp = expenses(cur), pInc = income(prev), pExp = expenses(prev);
        Totals totals = new Totals(inc.toPlainString(), exp.toPlainString(), inc.subtract(exp).toPlainString(),
                pct(inc, pInc), pct(exp, pExp), pct(inc.subtract(exp), pInc.subtract(pExp)));

        // trailing 12 calendar months ending with the month of `to`
        YearMonth endMonth = YearMonth.from(to), startMonth = endMonth.minusMonths(11);
        List<LedgerRow> year = ledger.flows(userId, currency, startMonth.atDay(1), endMonth.atEndOfMonth());
        List<MonthFlow> flow = new ArrayList<>();
        for (YearMonth m = startMonth; !m.isAfter(endMonth); m = m.plusMonths(1)) {
            final YearMonth month = m;
            List<LedgerRow> rows = year.stream().filter(r -> YearMonth.from(r.occurredOn()).equals(month)).toList();
            flow.add(new MonthFlow(month.toString(), income(rows).toPlainString(), expenses(rows).toPlainString()));
        }

        Map<UUID, Category> cats = categories.findAllVisibleTo(userId).stream().collect(Collectors.toMap(Category::getId, c -> c));
        Map<UUID, BigDecimal> byCat = new LinkedHashMap<>();
        BigDecimal spent = BigDecimal.ZERO;
        for (LedgerRow r : cur) {
            if (r.type() != TransactionType.EXPENSE) continue;
            BigDecimal v = r.amount().negate();
            byCat.merge(r.categoryId(), v, BigDecimal::add);   // HashMap-style merge allows the null key (uncategorised)
            spent = spent.add(v);
        }
        final BigDecimal spentTotal = spent;
        List<CategorySlice> slices = byCat.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Category c = e.getKey() == null ? null : cats.get(e.getKey());
                    double percent = spentTotal.signum() == 0 ? 0 : e.getValue().multiply(BigDecimal.valueOf(100)).divide(spentTotal, 1, RoundingMode.HALF_UP).doubleValue();
                    return new CategorySlice(e.getKey(), c == null ? "Uncategorised" : c.getName(), c == null ? "#6b7280" : c.getColor(), e.getValue().toPlainString(), percent);
                }).toList();

        List<FinanceTransaction> recentTx = transactions.findAll(TransactionSpecs.filter(userId, null, null, null, null, null, null),
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "occurredOn").and(Sort.by(Sort.Direction.DESC, "createdAt")))).getContent();
        Map<UUID, String> names = cats.values().stream().collect(Collectors.toMap(Category::getId, Category::getName));
        return new DashboardView(currency, from, to, total.toPlainString(), totals, flow, slices,
                ledgerService.views(userId, recentTx, names), all.stream().map(AccountService::view).toList());
    }

    private static BigDecimal income(List<LedgerRow> rows) {
        return rows.stream().filter(r -> r.type() == TransactionType.INCOME).map(LedgerRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal expenses(List<LedgerRow> rows) {
        return rows.stream().filter(r -> r.type() == TransactionType.EXPENSE).map(r -> r.amount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Null when the previous period was zero (a percentage would be meaningless). */
    private static Double pct(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
