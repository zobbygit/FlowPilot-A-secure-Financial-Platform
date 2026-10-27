package com.flowpilot.account;

import com.flowpilot.account.AccountDtos.*;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class AccountSettingsController {
    private final ProfileService profile;
    private final OnboardingService onboarding;
    private final CategoryService categories;

    public AccountSettingsController(ProfileService profile, OnboardingService onboarding, CategoryService categories) {
        this.profile = profile; this.onboarding = onboarding; this.categories = categories;
    }

    @GetMapping("/users/me/profile") public ProfileView profile(@AuthenticationPrincipal AuthPrincipal p) { return profile.profile(p.userId()); }
    @PutMapping("/users/me/profile") public ProfileView update(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody ProfileRequest r) { return profile.update(p.userId(), r); }

    @PreAuthorize("hasRole('USER')") @PostMapping("/users/me/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody ReauthRequest r, HttpServletRequest http) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowpilot-data-export.json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(profile.export(p, r.password(), ClientInfo.from(http)));
    }

    @PreAuthorize("hasRole('USER')") @PostMapping("/users/me/delete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody DeleteAccountRequest r, HttpServletRequest http) { profile.deleteAccount(p, r, ClientInfo.from(http)); }

    @PreAuthorize("hasRole('USER')") @GetMapping("/onboarding") public OnboardingState onboardingState(@AuthenticationPrincipal AuthPrincipal p) { return onboarding.state(p.userId()); }
    @PreAuthorize("hasRole('USER')") @PutMapping("/onboarding/draft") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void draft(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody DraftRequest r) { onboarding.saveDraft(p.userId(), r.draft()); }
    @PreAuthorize("hasRole('USER')") @PostMapping("/onboarding/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody OnboardingRequest r) { onboarding.complete(p.userId(), r); }

    @PreAuthorize("hasRole('USER')") @GetMapping("/categories/manage") public List<CategoryManageView> manage(@AuthenticationPrincipal AuthPrincipal p) { return categories.list(p.userId()); }
    @PreAuthorize("hasRole('USER')") @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CategoryManageView createCategory(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CategoryRequest r) { return categories.create(p.userId(), r); }
    @PreAuthorize("hasRole('USER')") @PutMapping("/categories/{id}")
    public CategoryManageView updateCategory(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody CategoryRequest r) { return categories.update(p.userId(), id, r); }
    @PreAuthorize("hasRole('USER')") @PostMapping("/categories/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { categories.setArchived(p.userId(), id, true); }
    @PreAuthorize("hasRole('USER')") @PostMapping("/categories/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { categories.setArchived(p.userId(), id, false); }
    @PreAuthorize("hasRole('USER')") @PutMapping("/categories/reorder") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody ReorderRequest r) { categories.reorder(p.userId(), r.ids()); }
}
