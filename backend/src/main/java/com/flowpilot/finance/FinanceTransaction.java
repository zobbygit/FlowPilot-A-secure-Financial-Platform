package com.flowpilot.finance;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class FinanceTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private TransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TxStatus status = TxStatus.POSTED;
    @Column(name = "occurred_on", nullable = false, updatable = false) private LocalDate occurredOn;
    @Column(updatable = false) private String description;
    @Column(updatable = false) private String merchant;
    @Column(name = "category_id", updatable = false) private UUID categoryId;
    @Column(name = "payment_method", updatable = false) private String paymentMethod;
    @Column(updatable = false) private String notes;
    @Column(name = "reverses_transaction_id", updatable = false) private UUID reversesTransactionId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "archived_at") private Instant archivedAt;

    protected FinanceTransaction() {}

    public FinanceTransaction(UUID userId, TransactionType type, LocalDate occurredOn, String description, String merchant,
                              UUID categoryId, String paymentMethod, String notes, UUID reversesTransactionId) {
        this.userId = userId; this.type = type; this.occurredOn = occurredOn; this.description = description;
        this.merchant = merchant; this.categoryId = categoryId; this.paymentMethod = paymentMethod;
        this.notes = notes; this.reversesTransactionId = reversesTransactionId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public TransactionType getType() { return type; }
    public TxStatus getStatus() { return status; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getDescription() { return description; }
    public String getMerchant() { return merchant; }
    public UUID getCategoryId() { return categoryId; }
    public String getPaymentMethod() { return paymentMethod; }
    public UUID getReversesTransactionId() { return reversesTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(TxStatus s) { this.status = s; }
}
