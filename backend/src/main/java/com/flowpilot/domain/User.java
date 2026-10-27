package com.flowpilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, updatable = false) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "failed_login_attempts", nullable = false) private int failedLoginAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "password_changed_at", nullable = false) private Instant passwordChangedAt = Instant.now();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected User() {}

    public User(String email, String passwordHash, Role role, UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPasswordHash(String h) { this.passwordHash = h; this.passwordChangedAt = Instant.now(); }
    public void setStatus(UserStatus s) { this.status = s; }
    public void setEmailVerifiedAt(Instant t) { this.emailVerifiedAt = t; }
    public void setFailedLoginAttempts(int n) { this.failedLoginAttempts = n; }
    public void setLockedUntil(Instant t) { this.lockedUntil = t; }
}
