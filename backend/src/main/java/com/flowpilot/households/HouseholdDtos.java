package com.flowpilot.households;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HouseholdDtos {
    private HouseholdDtos() {}

    public record CreateHousehold(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
    public record InviteRequest(@NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,29}$") String handle) {}
    public record ShareInput(@NotNull UUID userId, @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal value) {}
    public record ExpenseRequest(@NotBlank @Size(max = 120) String description, @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount, @NotNull LocalDate occurredOn, UUID categoryId,
                                 @NotNull UUID payerId, @NotBlank @Pattern(regexp = "EQUAL|EXACT|PERCENT") String splitType, @NotEmpty @Size(max = 20) List<@Valid ShareInput> participants) {}
    public record SettlementRequest(@NotNull UUID fromUser, @NotNull UUID toUser, @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount, @Size(max = 120) String note) {}
    public record BudgetRequest(@NotBlank @Size(max = 80) String name, UUID categoryId, @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal limitAmount) {}

    public record HouseholdSummary(UUID id, String name, String currency, boolean owner, int members, String myBalance) {}
    public record Member(UUID userId, String name, String role, String balance) {}
    public record Invitation(UUID id, UUID householdId, String householdName, String invitedBy, Instant expiresAt, String invitee, String status) {}
    public record Suggestion(String from, String to, String amount, UUID fromUser, UUID toUser) {}
    public record ExpenseView(UUID id, String description, String amount, LocalDate occurredOn, String payer, UUID payerId, String categoryName, String splitType, List<ShareView> shares, boolean canEdit) {}
    public record ShareView(UUID userId, String name, String share) {}
    public record BudgetView(UUID id, String name, String categoryName, String limit, String spent, double percentUsed) {}
    public record Detail(UUID id, String name, String currency, boolean owner, List<Member> members, List<Invitation> pendingInvitations, List<Suggestion> settleUp, List<BudgetView> budgets) {}
    public record PageOf<T>(List<T> items, int page, int size, long total) {}
}
