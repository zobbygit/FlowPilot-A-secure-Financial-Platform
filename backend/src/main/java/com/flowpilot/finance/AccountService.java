package com.flowpilot.finance;

import com.flowpilot.admin.SettingsService;
import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.Role;
import com.flowpilot.finance.FinanceDtos.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.flowpilot.audit.FinancialAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LedgerService ledgerService;
    private final AuditService audit;
    private final SettingsService settings;

    private final FinancialAudit fin;

    public AccountService(AccountRepository accounts, LedgerService ledgerService, AuditService audit, SettingsService settings, FinancialAudit fin) {
        this.fin = fin;
        this.settings = settings;
        this.accounts = accounts; this.ledgerService = ledgerService; this.audit = audit;
    }

    public List<AccountView> list(UUID userId) {
        return accounts.findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(userId).stream().map(AccountService::view).toList();
    }

    @Transactional
    public AccountView create(UUID userId, CreateAccountRequest r) {
        if (!settings.currencies().contains(r.currency())) throw ApiException.badRequest("UNSUPPORTED_CURRENCY", "That currency isn't supported. Supported: " + String.join(", ", settings.currencies()) + ".");
        Account a = accounts.save(new Account(userId, r.name().trim(), r.type(), r.currency(), r.allowNegative()));
        BigDecimal opening = r.openingBalance() == null ? BigDecimal.ZERO : r.openingBalance();
        if (opening.signum() > 0) {
            Account locked = accounts.findActiveForUpdate(a.getId(), userId).orElseThrow();
            ledgerService.postOpeningBalance(userId, locked, opening);
            a = locked;
        }
        fin.record(userId, userId, "ACCOUNT_CREATED", "ACCOUNT", a.getId(), a.getType() + " " + a.getName() + " (" + a.getCurrency() + ")", null, "opening " + opening.toPlainString());
        return view(a);
    }

    @Transactional
    public void archive(UUID userId, UUID accountId, ClientInfo ci) {
        Account a = accounts.findActiveForUpdate(accountId, userId).orElseThrow(() -> ApiException.notFound("Account not found."));
        if (a.getCurrentBalance().signum() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BALANCE_NOT_ZERO", "Move or spend the remaining balance before archiving this account.");
        }
        a.setArchivedAt(Instant.now());   // history stays intact; the account just leaves active lists
        accounts.save(a);
        fin.record(userId, userId, "ACCOUNT_ARCHIVED", "ACCOUNT", accountId, a.getName(), "ACTIVE", "ARCHIVED");
        audit.record(Role.USER, "ACCOUNT_ARCHIVED", true, userId, null, ci, null);
    }

    static AccountView view(Account a) {
        return new AccountView(a.getId(), a.getName(), a.getType(), a.getCurrency(), a.getCurrentBalance().toPlainString(), a.isAllowNegative());
    }
}
