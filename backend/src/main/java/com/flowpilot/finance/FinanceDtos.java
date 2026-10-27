package com.flowpilot.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Money crosses the API as decimal STRINGS in responses (no JS float rounding); requests accept string or number. */
public final class FinanceDtos {
    private FinanceDtos() {}

    public record CreateAccountRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull AccountType type,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Use a 3-letter currency code such as USD.") String currency,
            @DecimalMin(value = "0.0000") @Digits(integer = 15, fraction = 4) BigDecimal openingBalance,
            boolean allowNegative) {}

    public record AccountView(UUID id, String name, AccountType type, String currency, String currentBalance, boolean allowNegative) {}

    public record CategoryView(UUID id, String name, CategoryKind kind, String color) {}

    public record PostTransactionRequest(
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.0001", message = "Amount must be greater than zero.") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotNull LocalDate occurredOn,
            @NotNull UUID accountId,
            UUID toAccountId,
            UUID categoryId,
            @Size(max = 200) String description,
            @Size(max = 120) String merchant,
            @Size(max = 40) String paymentMethod,
            @Size(max = 2000) String notes) {}

    public record TransactionView(UUID id, TransactionType type, TxStatus status, LocalDate occurredOn, String description,
                                  String merchant, UUID categoryId, String categoryName, String amount, String currency,
                                  String accountName, String toAccountName, boolean reversal, boolean reversible) {}

    public record PageView<T>(List<T> items, int page, int size, long total) {}

    public record Money(String currency, String amount) {}

    public record Totals(String income, String expenses, String net, Double incomeChangePct, Double expenseChangePct, Double netChangePct) {}

    public record MonthFlow(String month, String income, String expenses) {}

    public record CategorySlice(UUID categoryId, String name, String color, String amount, double percent) {}

    public record DashboardView(String currency, LocalDate from, LocalDate to, String totalBalance, Totals totals,
                                List<MonthFlow> cashFlow, List<CategorySlice> spending,
                                List<TransactionView> recent, List<AccountView> accounts) {}
}
