package com.flowpilot.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AutomationDtos {
    private AutomationDtos() {}

    public record ActionDef(@NotBlank @Pattern(regexp = "GOAL|BILL|BUDGET") String targetType, @NotNull UUID targetId,
                            @NotBlank @Pattern(regexp = "FIXED|PERCENT") String amountType,
                            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amountValue) {}

    public record RuleRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "INCOME_RECEIVED|SCHEDULE|MANUAL") String triggerType,
            @Pattern(regexp = "WEEKLY|MONTHLY") String schedule, @Min(1) @Max(31) Integer scheduleDay,
            @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal minTriggerAmount, UUID triggerCategoryId,
            @NotNull UUID sourceAccountId, @Min(1) @Max(1000) Integer priority,
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal perRunLimit,
            @NotEmpty @Size(max = 10) List<@Valid ActionDef> actions) {}

    public record RuleView(UUID id, String name, String status, String triggerType, String schedule, Integer scheduleDay, String minTriggerAmount, UUID triggerCategoryId,
                           UUID sourceAccountId, int priority, String perRunLimit, List<ActionDef> actions, Instant lastRunAt, String lastRunStatus) {}

    public record Outcome(int position, String targetType, UUID targetId, String targetName, String requested, String allocated, String status, String message) {}
    public record Preview(String triggerAmount, String available, String perRunLimit, List<Outcome> outcomes, String totalAllocated, String note) {}
    public record PreviewRequest(@DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal incomeAmount) {}

    public record RunView(UUID id, UUID ruleId, String ruleName, String status, String triggerKey, String triggerAmount, String totalAllocated, String message, int attempt, UUID retryOf,
                          Instant createdAt, List<Outcome> outcomes, boolean retryable) {}
}
