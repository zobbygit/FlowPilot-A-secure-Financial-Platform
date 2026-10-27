package com.flowpilot.account;

import com.flowpilot.finance.FinanceDtos.CreateAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record ProfileView(String email, String fullName, String preferredCurrency, String timezone, String locale, String themePreference,
                              String numberFormat, String dateFormat, List<String> financialGoals, String avatarUrl, boolean onboardingCompleted) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String preferredCurrency,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String locale,
            @NotBlank @Pattern(regexp = "light|dark|system") String themePreference,
            @NotBlank @Pattern(regexp = "auto|comma|dot|space") String numberFormat,
            @NotBlank @Pattern(regexp = "auto|ymd|dmy|mdy") String dateFormat) {}

    /** Sensitive operations re-ask for the password. */
    public record ReauthRequest(@NotBlank @Size(max = 128) String password) {}

    public record DeleteAccountRequest(@NotBlank @Size(max = 128) String password, @NotBlank String confirmation) {}

    public record OnboardingAccount(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "CASH|BANK|SAVINGS|WALLET") String type,
                                    @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) java.math.BigDecimal openingBalance) {}

    public record OnboardingRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String preferredCurrency,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 8) List<@NotBlank @Size(max = 40) String> goals,
            @Size(max = 20) List<UUID> preferredCategoryIds,
            @Size(max = 10) List<@Valid OnboardingAccount> accounts) {}

    public record OnboardingState(boolean completed, String draft) {}

    public record DraftRequest(@Size(max = 4000) String draft) {}

    public record CategoryManageView(UUID id, String name, String kind, String color, int sortOrder, boolean archived, boolean builtIn, long usage) {}

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String kind,
                                  @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}

    public record ReorderRequest(@NotEmpty @Size(max = 200) List<UUID> ids) {}

    // keep import used for OpenAPI docs of onboarding accounts
    static CreateAccountRequest toCreate(OnboardingAccount a, String currency) {
        return new CreateAccountRequest(a.name(), com.flowpilot.finance.AccountType.valueOf(a.type()), currency, a.openingBalance(), false);
    }
}
