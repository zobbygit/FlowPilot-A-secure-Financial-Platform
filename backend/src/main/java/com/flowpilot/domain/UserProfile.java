package com.flowpilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "avatar_url") private String avatarUrl;
    @Column(name = "avatar_public_id") private String avatarPublicId;
    @Column(name = "preferred_currency", nullable = false) private String preferredCurrency = "USD";
    @Column(nullable = false) private String timezone = "UTC";
    @Column(nullable = false) private String locale = "en-US";
    @Column(name = "theme_preference", nullable = false) private String themePreference = "system";
    @Column(name = "onboarding_completed_at") private Instant onboardingCompletedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private long version;

    protected UserProfile() {}

    public UserProfile(UUID userId, String fullName) {
        this.userId = userId;
        this.fullName = fullName;
    }

    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getPreferredCurrency() { return preferredCurrency; }
    public String getThemePreference() { return themePreference; }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public void setFullName(String n) { this.fullName = n; }
}
