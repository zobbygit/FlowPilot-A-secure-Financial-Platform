package com.flowpilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Single-use, expiring token. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "email_tokens")
public class EmailToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private TokenPurpose purpose;
    @Column(name = "token_hash", nullable = false, updatable = false) private String tokenHash;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected EmailToken() {}

    public EmailToken(UUID userId, TokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public TokenPurpose getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
