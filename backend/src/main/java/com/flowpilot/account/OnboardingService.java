package com.flowpilot.account;

import com.flowpilot.account.AccountDtos.*;
import com.flowpilot.common.ApiException;
import com.flowpilot.finance.AccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public OnboardingService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public OnboardingState state(UUID userId) {
        return jdbc.query("select onboarding_completed_at, onboarding_draft from user_profiles where user_id = ?",
                (rs, i) -> new OnboardingState(rs.getTimestamp(1) != null, rs.getString(2)), userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    /** Lets the person leave and come back to an unfinished wizard. The draft is plain UI state, never financial data. */
    @Transactional
    public void saveDraft(UUID userId, String draft) {
        jdbc.update("update user_profiles set onboarding_draft = ? where user_id = ? and onboarding_completed_at is null", draft, userId);
    }

    /**
     * Atomic: preferences, preferred categories and every opening balance are committed together. Opening balances
     * are real OPENING_BALANCE ledger transactions (via AccountService), never a frontend-only number.
     */
    @Transactional
    public void complete(UUID userId, OnboardingRequest r) {
        if (state(userId).completed()) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_COMPLETED", "Onboarding is already complete.");
        ProfileService.requireZone(r.timezone());
        List<OnboardingAccount> list = r.accounts() == null ? List.of() : r.accounts();
        if (list.stream().map(a -> a.name().trim().toLowerCase()).distinct().count() != list.size()) throw ApiException.badRequest("DUPLICATE_ACCOUNT", "Give each account a different name.");

        if (r.preferredCategoryIds() != null) {
            for (UUID c : r.preferredCategoryIds().stream().distinct().toList()) {
                int ok = jdbc.update("insert into user_preferred_categories (user_id, category_id) select ?, id from categories where id = ? and (user_id is null or user_id = ?) on conflict do nothing", userId, c, userId);
                if (ok == 0 && jdbc.queryForObject("select count(*) from categories where id = ? and (user_id is null or user_id = ?)", Integer.class, c, userId) == 0) {
                    throw ApiException.badRequest("INVALID_CATEGORY", "One of the chosen categories is not available.");
                }
            }
        }
        for (OnboardingAccount a : list) accounts.create(userId, AccountDtos.toCreate(a, r.preferredCurrency()));
        jdbc.update("update user_profiles set preferred_currency = ?, timezone = ?, financial_goals = ?, onboarding_completed_at = now(), onboarding_draft = null, updated_at = now() where user_id = ?",
                r.preferredCurrency(), r.timezone(), ProfileService.joinGoals(r.goals()), userId);
    }
}
