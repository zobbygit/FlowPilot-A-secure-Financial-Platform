package com.flowpilot.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private AccountType type;
    @Column(nullable = false, updatable = false) private String currency;
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4) private BigDecimal currentBalance = BigDecimal.ZERO;
    @Column(name = "allow_negative", nullable = false) private boolean allowNegative;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private long version;

    protected Account() {}

    public Account(UUID userId, String name, AccountType type, String currency, boolean allowNegative) {
        this.userId = userId; this.name = name; this.type = type; this.currency = currency; this.allowNegative = allowNegative;
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
    public String getCurrency() { return currency; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public boolean isAllowNegative() { return allowNegative; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setCurrentBalance(BigDecimal b) { this.currentBalance = b; }
    public void setArchivedAt(Instant t) { this.archivedAt = t; }
}
