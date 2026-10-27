package com.flowpilot.finance;

import com.flowpilot.common.ApiException;
import com.flowpilot.finance.FinanceDtos.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.flowpilot.audit.FinancialAudit;
import java.util.*;
import java.util.stream.Collectors;
import com.flowpilot.automation.IncomePosted;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code allowed to change balances. Every change = immutable ledger entries + a locked,
 * constraint-checked balance update in ONE database transaction.
 */
@Service
public class LedgerService {
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final FinanceTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    private final FinancialAudit fin;

    public LedgerService(AccountRepository accounts, CategoryRepository categories, FinanceTransactionRepository transactions,
                         LedgerEntryRepository ledger, JdbcTemplate jdbc, ApplicationEventPublisher events, FinancialAudit fin) {
        this.fin = fin;
        this.events = events;
        this.accounts = accounts; this.categories = categories; this.transactions = transactions;
        this.ledger = ledger; this.jdbc = jdbc;
    }

    @Transactional
    public TransactionView post(UUID userId, PostTransactionRequest r, String idempotencyKey) {
        if (r.type() != TransactionType.INCOME && r.type() != TransactionType.EXPENSE && r.type() != TransactionType.TRANSFER) {
            throw ApiException.badRequest("UNSUPPORTED_TYPE", "Only income, expense and transfer can be posted here.");
        }
        if (r.occurredOn().isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) {
            throw ApiException.badRequest("FUTURE_DATE", "Transactions cannot be dated in the future. Use bills or automation for scheduled items.");
        }
        String key = normalizeKey(idempotencyKey);
        String requestHash = key == null ? null : hash(userId, r);
        if (key != null) {
            List<Map<String, Object>> found = jdbc.queryForList(
                    "select request_hash, resource_id from idempotency_keys where user_id = ? and key = ? and operation = 'POST_TRANSACTION'", userId, key);
            if (!found.isEmpty()) {
                if (!requestHash.equals(found.get(0).get("request_hash"))) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", "That idempotency key was used with a different request.");
                }
                UUID existing = (UUID) found.get(0).get("resource_id");
                return view(userId, transactions.findByIdAndUserId(existing, userId).orElseThrow(), null);
            }
        }

        validateCategory(userId, r);
        FinanceTransaction tx = transactions.save(new FinanceTransaction(userId, r.type(), r.occurredOn(), clean(r.description()),
                clean(r.merchant()), r.type() == TransactionType.TRANSFER ? null : r.categoryId(), clean(r.paymentMethod()), clean(r.notes()), null));

        if (r.type() == TransactionType.TRANSFER) {
            if (r.toAccountId() == null || r.toAccountId().equals(r.accountId())) {
                throw ApiException.badRequest("INVALID_TRANSFER", "Choose two different accounts for a transfer.");
            }
            Map<UUID, Account> locked = lockInOrder(userId, r.accountId(), r.toAccountId());
            Account from = locked.get(r.accountId()), to = locked.get(r.toAccountId());
            if (!from.getCurrency().equals(to.getCurrency())) {
                throw ApiException.badRequest("CURRENCY_MISMATCH", "Transfers between different currencies are not supported yet.");
            }
            apply(tx, from, r.amount().negate());
            apply(tx, to, r.amount());
        } else {
            Account a = lockInOrder(userId, r.accountId()).get(r.accountId());
            apply(tx, a, r.type() == TransactionType.INCOME ? r.amount() : r.amount().negate());
            if (r.type() == TransactionType.INCOME) events.publishEvent(new IncomePosted(userId, tx.getId(), a.getId(), r.categoryId(), r.amount(), a.getCurrency()));
        }
        if (key != null) {
            jdbc.update("insert into idempotency_keys (user_id, key, operation, request_hash, resource_id) values (?, ?, 'POST_TRANSACTION', ?, ?)",
                    userId, key, requestHash, tx.getId());
        }
        fin.record(userId, userId, "TRANSACTION_CREATED", "TRANSACTION", tx.getId(), r.type() + " " + r.amount().toPlainString() + (r.type() == TransactionType.TRANSFER ? " (transfer)" : ""), null, "POSTED");
        return view(userId, tx, null);
    }

    /** Correction = a mirror-image posting linked to the original. Nothing is edited or deleted. */
    @Transactional
    public TransactionView reverse(UUID userId, UUID transactionId) {
        FinanceTransaction original = transactions.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found."));
        if (original.getStatus() != TxStatus.POSTED || original.getReversesTransactionId() != null
                || original.getType() == TransactionType.OPENING_BALANCE) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_REVERSIBLE", "This transaction cannot be reversed.");
        }
        List<LedgerEntry> entries = ledger.findByTransactionId(original.getId());
        UUID[] ids = entries.stream().map(LedgerEntry::getAccountId).distinct().toArray(UUID[]::new);
        Map<UUID, Account> locked = lockInOrder(userId, ids);

        FinanceTransaction reversal = transactions.save(new FinanceTransaction(userId, original.getType(), LocalDate.now(ZoneOffset.UTC),
                "Reversal: " + (original.getDescription() == null ? original.getType().name().toLowerCase() : original.getDescription()),
                original.getMerchant(), original.getCategoryId(), original.getPaymentMethod(), null, original.getId()));
        for (LedgerEntry e : entries) apply(reversal, locked.get(e.getAccountId()), e.getAmount().negate());
        original.setStatus(TxStatus.REVERSED);
        transactions.save(original);
        fin.record(userId, userId, "TRANSACTION_REVERSED", "TRANSACTION", original.getId(), original.getType() + " reversed by " + reversal.getId(), "POSTED", "REVERSED");
        return view(userId, reversal, null);
    }

    /** Used by AccountService for opening balances. Caller must hold the account row lock. */
    @Transactional
    public void postOpeningBalance(UUID userId, Account account, BigDecimal amount) {
        FinanceTransaction tx = transactions.save(new FinanceTransaction(userId, TransactionType.OPENING_BALANCE,
                LocalDate.now(ZoneOffset.UTC), "Opening balance", null, null, null, null, null));
        apply(tx, account, amount);
        fin.record(userId, userId, "OPENING_BALANCE_RECORDED", "ACCOUNT", account.getId(), "Opening balance " + amount.toPlainString() + " " + account.getCurrency(), null, amount.toPlainString());
    }

    // ------------------------------------------------------------------ views

    public TransactionView view(UUID userId, FinanceTransaction t, Map<UUID, String> categoryNames) {
        return views(userId, List.of(t), categoryNames).get(0);
    }

    public List<TransactionView> views(UUID userId, List<FinanceTransaction> txs, Map<UUID, String> knownCategoryNames) {
        if (txs.isEmpty()) return List.of();
        Map<UUID, List<LedgerEntry>> entries = ledger.findByTransactionIdIn(txs.stream().map(FinanceTransaction::getId).toList())
                .stream().collect(Collectors.groupingBy(LedgerEntry::getTransactionId));
        Map<UUID, String> accountNames = accounts.findAllById(entries.values().stream().flatMap(List::stream)
                .map(LedgerEntry::getAccountId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Account::getId, Account::getName));
        Map<UUID, String> catNames = knownCategoryNames != null ? knownCategoryNames
                : categories.findAllVisibleTo(userId).stream().collect(Collectors.toMap(Category::getId, Category::getName));

        return txs.stream().map(t -> {
            List<LedgerEntry> es = entries.getOrDefault(t.getId(), List.of());
            BigDecimal amount; String currency = es.isEmpty() ? "" : es.get(0).getCurrency();
            String accountName = null, toName = null;
            if (t.getType() == TransactionType.TRANSFER) {
                LedgerEntry out = es.stream().filter(e -> e.getAmount().signum() < 0).findFirst().orElse(null);
                LedgerEntry in = es.stream().filter(e -> e.getAmount().signum() > 0).findFirst().orElse(null);
                amount = in == null ? BigDecimal.ZERO : in.getAmount();
                accountName = out == null ? null : accountNames.get(out.getAccountId());
                toName = in == null ? null : accountNames.get(in.getAccountId());
            } else {
                amount = es.stream().map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                accountName = es.isEmpty() ? null : accountNames.get(es.get(0).getAccountId());
            }
            boolean reversal = t.getReversesTransactionId() != null;
            boolean reversible = !reversal && t.getStatus() == TxStatus.POSTED && t.getType() != TransactionType.OPENING_BALANCE;
            return new TransactionView(t.getId(), t.getType(), t.getStatus(), t.getOccurredOn(), t.getDescription(), t.getMerchant(),
                    t.getCategoryId(), t.getCategoryId() == null ? null : catNames.get(t.getCategoryId()), amount.toPlainString(),
                    currency, accountName, toName, reversal, reversible);
        }).toList();
    }

    // ------------------------------------------------------------------ internals

    private void apply(FinanceTransaction tx, Account account, BigDecimal signed) {
        BigDecimal next = account.getCurrentBalance().add(signed);
        if (!account.isAllowNegative() && next.signum() < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Not enough funds in " + account.getName() + ".");
        }
        account.setCurrentBalance(next);
        accounts.save(account);
        ledger.save(new LedgerEntry(tx.getId(), account.getId(), account.getUserId(), signed, account.getCurrency()));
    }

    /** Locks in ascending id order so two concurrent transfers can never deadlock. */
    private Map<UUID, Account> lockInOrder(UUID userId, UUID... ids) {
        Map<UUID, Account> out = new LinkedHashMap<>();
        Arrays.stream(ids).distinct().sorted().forEach(id -> out.put(id, accounts.findActiveForUpdate(id, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."))));
        return out;
    }

    private void validateCategory(UUID userId, PostTransactionRequest r) {
        if (r.categoryId() == null || r.type() == TransactionType.TRANSFER) return;
        Category c = categories.findById(r.categoryId())
                .filter(x -> x.getUserId() == null || x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "That category is not available."));
        CategoryKind expected = r.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (c.getKind() != expected) throw ApiException.badRequest("INVALID_CATEGORY", "That category does not match the transaction type.");
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String normalizeKey(String k) {
        if (k == null || k.isBlank()) return null;
        if (k.length() > 80) throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is too long.");
        return k.trim();
    }

    private static String hash(UUID userId, PostTransactionRequest r) {
        String canonical = String.join("|", userId.toString(), r.type().name(), r.amount().stripTrailingZeros().toPlainString(),
                r.occurredOn().toString(), String.valueOf(r.accountId()), String.valueOf(r.toAccountId()), String.valueOf(r.categoryId()),
                String.valueOf(r.description()), String.valueOf(r.merchant()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}