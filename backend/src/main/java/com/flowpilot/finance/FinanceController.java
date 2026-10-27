package com.flowpilot.finance;

import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.Role;
import com.flowpilot.finance.FinanceDtos.*;
import com.flowpilot.repo.UserRepository;
import com.flowpilot.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** User finance API. Every query is scoped by the authenticated principal's id - ids in URLs are never trusted for ownership. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('USER')")
public class FinanceController {
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final DashboardService dashboard;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final FinanceTransactionRepository transactions;

    public FinanceController(AccountService accountService, LedgerService ledgerService, DashboardService dashboard,
                             AccountRepository accounts, CategoryRepository categories, FinanceTransactionRepository transactions) {
        this.accountService = accountService; this.ledgerService = ledgerService; this.dashboard = dashboard;
        this.accounts = accounts; this.categories = categories; this.transactions = transactions;
    }

    @GetMapping("/accounts")
    public List<AccountView> listAccounts(@AuthenticationPrincipal AuthPrincipal p) {
        return accountService.list(p.userId());
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView createAccount(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CreateAccountRequest r) {
        return accountService.create(p.userId(), r);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveAccount(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) {
        accountService.archive(p.userId(), id, ClientInfo.from(http));
    }

    @GetMapping("/categories")
    public List<CategoryView> listCategories(@AuthenticationPrincipal AuthPrincipal p) {
        return categories.findVisibleTo(p.userId()).stream().map(c -> new CategoryView(c.getId(), c.getName(), c.getKind(), c.getColor())).toList();
    }

    @GetMapping("/transactions")
    public PageView<TransactionView> listTransactions(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<FinanceTransaction> result = transactions.findAll(
                TransactionSpecs.filter(p.userId(), from, to, type, categoryId, accountId, q),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "occurredOn").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        return new PageView<>(ledgerService.views(p.userId(), result.getContent(), null), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView post(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody PostTransactionRequest r,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ledgerService.post(p.userId(), r, idempotencyKey);
    }

    @PostMapping("/transactions/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView reverse(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
        return ledgerService.reverse(p.userId(), id);
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        return dashboard.summary(p.userId(), from, to, currency);
    }
}
