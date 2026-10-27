package com.flowpilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, updatable = false) private String tokenHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "last_active_at", nullable = false) private Instant lastActiveAt = Instant.now();
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_reason") private String revokedReason;
    @Column(name = "user_agent", updatable = false) private String userAgent;
    @Column(name = "ip_address", updatable = false) private String ipAddress;

    protected AuthSession() {}

    public AuthSession(UUID userId, String tokenHash, Instant expiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setLastActiveAt(Instant t) { this.lastActiveAt = t; }
    public void revoke(String reason, Instant at) { this.revokedAt = at; this.revokedReason = reason; }
}
