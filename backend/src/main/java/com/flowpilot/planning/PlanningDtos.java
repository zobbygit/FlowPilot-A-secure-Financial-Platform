package com.flowpilot.planning;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Money in responses is a decimal string. */
public final class PlanningDtos {
    private PlanningDtos() {}

    // ---------- budgets
    public record BudgetRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "MONTHLY|CUSTOM") String periodType,
            LocalDate startDate, LocalDate endDate, UUID categoryId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal limitAmount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            boolean rollover,
            @Size(max = 5) List<@Min(1) @Max(200) Integer> thresholds) {}

    public record BudgetView(UUID id, String name, String periodType, LocalDate periodStart, LocalDate periodEnd,
                             UUID categoryId, String categoryName, String limit, String currency, boolean rollover,
                             List<Integer> thresholds, String carriedOver, String spent, String remaining,
                             double percentUsed, String state, List<Integer> crossedThresholds) {}

    // ---------- bills
    public record BillRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "BILL|SUBSCRIPTION") String kind,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Pattern(regexp = "WEEKLY|MONTHLY|QUARTERLY|YEARLY") String frequency,
            @NotNull LocalDate nextDueDate, UUID categoryId, UUID accountId,
            @Min(0) @Max(60) Integer reminderDaysBefore,
            @Size(max = 1000) String notes) {}

    public record BillStatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|PAUSED|CANCELLED|NEEDS_REVIEW") String status) {}

    public record PayBillRequest(@NotNull UUID accountId) {}

    public record BillView(UUID id, String name, String kind, String amount, String currency, String frequency,
                           LocalDate nextDueDate, String status, UUID categoryId, UUID accountId, int reminderDaysBefore,
                           String notes, LocalDate lastPaidOn, long daysUntilDue, boolean reminderDue, boolean overdue,
                           String monthlyEstimate, String previousAmount) {}

    public record CostEstimate(String currency, String monthly, String annual) {}

    public record BillsOverview(List<BillView> bills, List<CostEstimate> estimates) {}

    // ---------- goals
    public record GoalRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "EMERGENCY|TRAVEL|GADGET|OTHER") String goalType,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal targetAmount,
            LocalDate targetDate,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal contributionAmount,
            @Pattern(regexp = "WEEKLY|MONTHLY") String contributionFrequency) {}

    public record AllocationRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount) {}

    public record GoalTransferRequest(
            @NotNull UUID fromAccountId, @NotNull UUID toAccountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount) {}

    /** "saved" = real transfers only. "planned" = intentions that have NOT moved money. */
    public record GoalView(UUID id, String name, String goalType, String targetAmount, LocalDate targetDate, String currency,
                           String contributionAmount, String contributionFrequency, String status, String saved, String planned,
                           double percentSaved, String remaining, String suggestedMonthly) {}
}
