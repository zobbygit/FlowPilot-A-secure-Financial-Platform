package com.flowpilot.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Insert-only (DB trigger forbids UPDATE/DELETE). */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "transaction_id", nullable = false, updatable = false) private UUID transactionId;
    @Column(name = "account_id", nullable = false, updatable = false) private UUID accountId;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, updatable = false) private String currency;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected LedgerEntry() {}

    public LedgerEntry(UUID transactionId, UUID accountId, UUID userId, BigDecimal amount, String currency) {
        this.transactionId = transactionId; this.accountId = accountId; this.userId = userId;
        this.amount = amount; this.currency = currency;
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
}
