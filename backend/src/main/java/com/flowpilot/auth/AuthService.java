package com.flowpilot.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.flowpilot.audit.AuditService;
import com.flowpilot.auth.AuthDtos.*;
import com.flowpilot.auth.AuthDtos.ChangePasswordRequest;
import com.flowpilot.auth.AuthDtos.LoginRequest;
import com.flowpilot.auth.AuthDtos.MeResponse;
import com.flowpilot.auth.AuthDtos.RegisterRequest;
import com.flowpilot.auth.AuthDtos.ResetPasswordRequest;
import com.flowpilot.auth.AuthDtos.SessionView;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.config.AppProperties;
import com.flowpilot.domain.EmailToken;
import com.flowpilot.domain.Role;
import com.flowpilot.domain.TokenPurpose;
import com.flowpilot.domain.User;
import com.flowpilot.domain.UserProfile;
import com.flowpilot.domain.UserStatus;
import com.flowpilot.mail.EmailDeliveryException;
import com.flowpilot.mail.EmailSender;
import com.flowpilot.mail.EmailTemplates;
import com.flowpilot.mail.OutgoingEmail;
import com.flowpilot.repo.EmailTokenRepository;
import com.flowpilot.repo.UserProfileRepository;
import com.flowpilot.repo.UserRepository;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.security.SessionService;
import com.flowpilot.security.TokenService;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    public record LoginResult(MeResponse me, String rawToken, Duration maxAge) {}

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final EmailTokenRepository emailTokens;
    private final TokenService tokenService;
    private final PasswordEncoder encoder;
    private final EmailSender emailSender;
    private final SessionService sessions;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final String dummyHash;   // burns equal CPU for unknown accounts (timing side-channel)

    public AuthService(UserRepository users, UserProfileRepository profiles, EmailTokenRepository emailTokens,
                       TokenService tokenService, PasswordEncoder encoder, EmailSender emailSender,
                       SessionService sessions, AuditService audit, RateLimiter limiter, AppProperties props) {
        this.users = users; this.profiles = profiles; this.emailTokens = emailTokens;
        this.tokenService = tokenService; this.encoder = encoder; this.emailSender = emailSender;
        this.sessions = sessions; this.audit = audit; this.limiter = limiter; this.props = props;
        this.dummyHash = encoder.encode("flowpilot-timing-equaliser");
    }

    // ---------------------------------------------------------------- registration & verification

    /** Always looks the same to the caller whether or not the address already exists. */
    @Transactional
    public void register(RegisterRequest req, ClientInfo ci) {
        String email = normalize(req.email());
        limiter.check("register-ip", ci.ip(), 10, Duration.ofHours(1));
        limiter.check("register-email", email, 5, Duration.ofHours(1));
        PasswordPolicy.requireValid(req.password(), email);

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() == Role.USER && u.getStatus() == UserStatus.PENDING_VERIFICATION) issueVerification(u);
            else log.info("Registration attempted for an existing account; no email sent");
            return;
        }
        User u = users.save(new User(email, encoder.encode(req.password()), Role.USER, UserStatus.PENDING_VERIFICATION));
        profiles.save(new UserProfile(u.getId(), req.fullName().trim()));
        UUID newUserId = u.getId();
        // Audit runs in its own transaction (REQUIRES_NEW), so it must wait until the user row is committed (FK).
        afterCommit(() -> audit.record(Role.USER, "ACCOUNT_REGISTERED", true, newUserId, null, ci, null));
        issueVerification(u);
    }

    @Transactional
    public void resendVerification(String rawEmail, ClientInfo ci) {
        String email = normalize(rawEmail);
        limiter.check("resend-ip", ci.ip(), 10, Duration.ofHours(1));
        limiter.check("resend-email", email, 3, Duration.ofHours(1));
        users.findByEmail(email)
                .filter(u -> u.getRole() == Role.USER && u.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueVerification);
    }

    @Transactional
    public void verifyEmail(String rawToken, ClientInfo ci) {
        limiter.check("verify-ip", ci.ip(), 30, Duration.ofHours(1));
        EmailToken t = loadUsableToken(rawToken, TokenPurpose.VERIFY_EMAIL);
        Instant now = Instant.now();
        if (emailTokens.consume(t.getId(), now) == 0) throw ApiException.badRequest("TOKEN_USED", "This link has already been used.");
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "This link is not valid."));
        if (u.getEmailVerifiedAt() == null) u.setEmailVerifiedAt(now);
        if (u.getStatus() == UserStatus.PENDING_VERIFICATION) u.setStatus(UserStatus.ACTIVE);
        users.save(u);
        audit.record(u.getRole(), "EMAIL_VERIFIED", true, u.getId(), null, ci, null);
    }

    // ---------------------------------------------------------------- login / logout

    public LoginResult login(LoginRequest req, Role expectedRole, ClientInfo ci) {
        String email = normalize(req.email());
        limiter.check("login-ip", ci.ip(), 30, Duration.ofMinutes(10));
        limiter.check("login-email", email, 10, Duration.ofMinutes(10));
        Instant now = Instant.now();

        User u = users.findByEmail(email).filter(x -> x.getRole() == expectedRole).orElse(null);
        boolean locked = u != null && u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        boolean passwordOk = encoder.matches(req.password(), u != null ? u.getPasswordHash() : dummyHash);

        if (u == null || locked || !passwordOk) {
            if (u != null && !locked) registerFailure(u, now);
            audit.record(expectedRole, "LOGIN_FAILED", false, u == null ? null : u.getId(), null, ci, locked ? "locked" : null);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        if (u.getStatus() == UserStatus.SUSPENDED) {
            audit.record(expectedRole, "LOGIN_BLOCKED_SUSPENDED", false, u.getId(), null, ci, null);
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended. Please contact support.");
        }
        if (u.getEmailVerifiedAt() == null || u.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email before logging in.");
        }
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        if (u.getFailedLoginAttempts() > 0 || u.getLockedUntil() != null) {
            u.setFailedLoginAttempts(0);
            u.setLockedUntil(null);
            users.save(u);
        }
        SessionService.Created created = sessions.create(u.getId(), ci);
        audit.record(u.getRole(), "LOGIN_SUCCEEDED", true, u.getId(), created.session().getId(), ci, null);
        return new LoginResult(toMe(u), created.rawToken(), props.sessions().absoluteTimeout());
    }

    public void logout(AuthPrincipal principal, ClientInfo ci) {
        if (principal == null) return;
        sessions.revoke(principal.sessionId(), principal.userId(), principal.role(), "LOGOUT", ci);
        audit.record(principal.role(), "LOGOUT", true, principal.userId(), principal.sessionId(), ci, null);
    }

    public MeResponse me(AuthPrincipal p) {
        User u = users.findById(p.userId()).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please sign in."));
        return toMe(u);
    }

    // ---------------------------------------------------------------- password reset / change

    /** Generic outcome: never reveals whether the address has an account. Admin accounts are excluded by design. */
    @Transactional
    public void forgotPassword(String rawEmail, ClientInfo ci) {
        String email = normalize(rawEmail);
        limiter.check("forgot-ip", ci.ip(), 10, Duration.ofHours(1));
        limiter.check("forgot-email", email, 3, Duration.ofHours(1));
        users.findByEmail(email)
                .filter(u -> u.getRole() == Role.USER && u.getStatus() == UserStatus.ACTIVE)
                .ifPresent(u -> {
                    Instant now = Instant.now();
                    emailTokens.revokeOutstanding(u.getId(), TokenPurpose.RESET_PASSWORD, now);
                    TokenService.IssuedToken t = tokenService.issue();
                    Duration ttl = props.tokens().resetPasswordTtl();
                    emailTokens.save(new EmailToken(u.getId(), TokenPurpose.RESET_PASSWORD, t.hash(), now.plus(ttl)));
                    String link = base() + "/user/reset-password?token=" + t.raw();
                    OutgoingEmail mail = EmailTemplates.passwordReset(u.getEmail(), nameOf(u), link, ttl.toMinutes());
                    afterCommit(() -> sendSafely(mail, "password reset"));
                    audit.record(Role.USER, "PASSWORD_RESET_REQUESTED", true, u.getId(), null, ci, null);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req, ClientInfo ci) {
        limiter.check("reset-ip", ci.ip(), 20, Duration.ofHours(1));
        EmailToken t = loadUsableToken(req.token(), TokenPurpose.RESET_PASSWORD);
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "This link is not valid."));
        PasswordPolicy.requireValid(req.password(), u.getEmail());
        if (emailTokens.consume(t.getId(), Instant.now()) == 0) throw ApiException.badRequest("TOKEN_USED", "This link has already been used.");
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        sessions.revokeAll(u.getId(), "PASSWORD_RESET");        // every existing session dies
        audit.record(u.getRole(), "PASSWORD_RESET_COMPLETED", true, u.getId(), null, ci, null);
    }

    @Transactional
    public void changePassword(AuthPrincipal p, ChangePasswordRequest req, ClientInfo ci) {
        limiter.check("change-password", p.userId().toString(), 5, Duration.ofMinutes(10));
        User u = users.findById(p.userId()).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please sign in."));
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            audit.record(p.role(), "PASSWORD_CHANGE_FAILED", false, u.getId(), p.sessionId(), ci, null);
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Your current password is incorrect.");
        }
        PasswordPolicy.requireValid(req.newPassword(), u.getEmail());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
        sessions.revokeOthers(u.getId(), p.sessionId(), "PASSWORD_CHANGED");
        audit.record(p.role(), "PASSWORD_CHANGED", true, u.getId(), p.sessionId(), ci, null);
    }

    // ---------------------------------------------------------------- internals

private void issueVerification(User u) {
    Instant now = Instant.now();
    emailTokens.revokeOutstanding(u.getId(), TokenPurpose.VERIFY_EMAIL, now);
    TokenService.IssuedToken t = tokenService.issue();
    Duration ttl = props.tokens().verifyEmailTtl();
    emailTokens.save(new EmailToken(u.getId(), TokenPurpose.VERIFY_EMAIL, t.hash(), now.plus(ttl)));
    
    // FIX: Strip whitespace and trailing slashes to ensure a clean URL
    String cleanBase = base().strip();
    if (cleanBase.endsWith("/")) {
        cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
    }
    
    String link = cleanBase + "/user/verify-email?token=" + t.raw();
    
    OutgoingEmail mail = EmailTemplates.verification(u.getEmail(), nameOf(u), link, ttl.toHours());
    afterCommit(() -> sendSafely(mail, "verification"));
}

    private EmailToken loadUsableToken(String rawToken, TokenPurpose purpose) {
        EmailToken t = emailTokens.findByTokenHash(tokenService.hash(rawToken))
                .filter(x -> x.getPurpose() == purpose)
                .orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "This link is not valid."));
        if (t.getRevokedAt() != null) throw ApiException.badRequest("TOKEN_REVOKED", "This link was replaced by a newer one.");
        if (t.getConsumedAt() != null) throw ApiException.badRequest("TOKEN_USED", "This link has already been used.");
        if (t.getExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("TOKEN_EXPIRED", "This link has expired.");
        return t;
    }

    private void registerFailure(User u, Instant now) {
        int n = u.getFailedLoginAttempts() + 1;
        u.setFailedLoginAttempts(n);
        if (n >= MAX_FAILED_LOGINS) u.setLockedUntil(now.plus(LOCK_DURATION));
        users.save(u);
    }

    private void sendSafely(OutgoingEmail mail, String kind) {
        try {
            emailSender.send(mail);
        } catch (EmailDeliveryException e) {
            // "Requested" is not "delivered": we only record that the provider did not accept it.
            log.warn("The {} email was not accepted by the provider (status={})", kind, e.status());
        }
    }

    private MeResponse toMe(User u) {
        UserProfile p = profiles.findById(u.getId()).orElse(null);
        return new MeResponse(u.getId(), u.getEmail(), p == null ? "" : p.getFullName(), u.getRole(),
                u.getEmailVerifiedAt() != null, p == null ? null : p.getAvatarUrl(),
                p == null ? "system" : p.getThemePreference(), p != null && p.getOnboardingCompletedAt() != null);
    }

    private String nameOf(User u) {
        return profiles.findById(u.getId()).map(UserProfile::getFullName).orElse("there");
    }

    private String base() {
        String b = props.publicBaseUrl();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safely(r); }
            });
        } else {
            safely(r);
        }
    }

    /** Post-commit side effects (email, audit) must never turn a committed success into an error response. */
    private static void safely(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { log.error("Post-commit action failed", e); }
    }

    public List<SessionView> listSessions(AuthPrincipal p) {
        Instant now = Instant.now();
        return sessions.list(p.userId()).stream().map(s -> {
            String status = s.getRevokedAt() != null ? "REVOKED"
                    : (s.getExpiresAt().isBefore(now) || s.getLastActiveAt().plus(props.sessions().idleTimeout()).isBefore(now)) ? "EXPIRED"
                    : "ACTIVE";
            return new SessionView(s.getId(), s.getId().equals(p.sessionId()), s.getUserAgent(), s.getIpAddress(),
                    s.getCreatedAt(), s.getLastActiveAt(), s.getExpiresAt(), status);
        }).toList();
    }

    public void revokeSession(AuthPrincipal p, UUID sessionId, ClientInfo ci) {
        sessions.revoke(sessionId, p.userId(), p.role(), "USER_REVOKED", ci);
    }

    public int revokeOtherSessions(AuthPrincipal p, ClientInfo ci) {
        int n = sessions.revokeOthers(p.userId(), p.sessionId(), "USER_REVOKED_OTHERS");
        audit.record(p.role(), "SESSIONS_REVOKED_OTHERS", true, p.userId(), p.sessionId(), ci, "count=" + n);
        return n;
    }
}
