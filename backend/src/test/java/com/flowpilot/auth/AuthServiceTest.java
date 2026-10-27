package com.flowpilot.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flowpilot.audit.AuditService;
import com.flowpilot.auth.AuthDtos.*;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.config.AppProperties;
import com.flowpilot.domain.*;
import com.flowpilot.mail.EmailSender;
import com.flowpilot.mail.OutgoingEmail;
import com.flowpilot.repo.EmailTokenRepository;
import com.flowpilot.repo.UserProfileRepository;
import com.flowpilot.repo.UserRepository;
import com.flowpilot.security.SessionService;
import com.flowpilot.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String EMAIL = "ana@example.com";
    private static final String STRONG = "Tr4vel-Light-Orbit!";
    private static final ClientInfo CI = new ClientInfo("203.0.113.9", "JUnit");

    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock EmailTokenRepository emailTokens;
    @Mock PasswordEncoder encoder;
    @Mock EmailSender emailSender;
    @Mock SessionService sessions;
    @Mock AuditService audit;

    private final TokenService tokenService = new TokenService();
    private final AppProperties props = new AppProperties("https://app.example.test",
            new AppProperties.Cors(List.of("https://app.example.test")),
            new AppProperties.Cookie("fp_session", true, "Lax"),
            new AppProperties.Sessions(Duration.ofHours(2), Duration.ofDays(7)),
            new AppProperties.Tokens(Duration.ofHours(24), Duration.ofMinutes(30)),
            new AppProperties.Brevo(false, "", "", ""),
            new AppProperties.Admin("", "", "", false),
            new AppProperties.Cloudinary("", "", ""));
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("hash");
        service = new AuthService(users, profiles, emailTokens, tokenService, encoder, emailSender,
                sessions, audit, new RateLimiter(), props);
    }

    // ---- registration

    @Test
    void registerCreatesUnverifiedUserAndEmailsASingleUseLinkWhoseHashIsStored() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.register(new RegisterRequest("Ana Diaz", "  ANA@Example.com ", STRONG, true), CI);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertEquals(EMAIL, user.getValue().getEmail());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getValue().getStatus());
        assertEquals(Role.USER, user.getValue().getRole());
        assertNull(user.getValue().getEmailVerifiedAt());

        ArgumentCaptor<OutgoingEmail> mail = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(mail.capture());
        String text = mail.getValue().text();
        String prefix = "https://app.example.test/user/verify-email?token=";
        int start = text.indexOf(prefix);
        assertTrue(start >= 0, "email must contain the verification link");
        String rawToken = text.substring(start + prefix.length()).split("\\s")[0];

        ArgumentCaptor<EmailToken> stored = ArgumentCaptor.forClass(EmailToken.class);
        verify(emailTokens).save(stored.capture());
        assertEquals(tokenService.hash(rawToken), stored.getValue().getTokenHash());
        assertNotEquals(rawToken, stored.getValue().getTokenHash());
        assertEquals(TokenPurpose.VERIFY_EMAIL, stored.getValue().getPurpose());
        assertTrue(stored.getValue().getExpiresAt().isAfter(Instant.now().plus(Duration.ofHours(23))));
    }

    @Test
    void registerForAnExistingActiveAccountSendsNothingAndCreatesNothing() {
        User existing = new User(EMAIL, "hash", Role.USER, UserStatus.ACTIVE);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> service.register(new RegisterRequest("Ana", EMAIL, STRONG, true), CI));

        verifyNoInteractions(emailSender);
        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsWeakPasswordsServerSide() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.register(new RegisterRequest("Ana", EMAIL, "weak", true), CI));
        assertEquals("WEAK_PASSWORD", ex.code());
        verifyNoInteractions(emailSender);
    }

    // ---- verification

    @Test
    void verifyEmailConsumesTheTokenAndActivatesTheAccount() {
        UUID userId = UUID.randomUUID();
        TokenService.IssuedToken t = tokenService.issue();
        EmailToken token = new EmailToken(userId, TokenPurpose.VERIFY_EMAIL, t.hash(), Instant.now().plusSeconds(3600));
        User user = new User(EMAIL, "hash", Role.USER, UserStatus.PENDING_VERIFICATION);
        when(emailTokens.findByTokenHash(t.hash())).thenReturn(Optional.of(token));
        when(emailTokens.consume(any(), any())).thenReturn(1);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.verifyEmail(t.raw(), CI);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getEmailVerifiedAt());
        verify(audit).record(eq(Role.USER), eq("EMAIL_VERIFIED"), eq(true), any(), isNull(), any(), isNull());
    }

    @Test
    void verifyEmailTokenCannotBeReusedWhenAnotherRequestWonTheRace() {
        TokenService.IssuedToken t = tokenService.issue();
        EmailToken token = new EmailToken(UUID.randomUUID(), TokenPurpose.VERIFY_EMAIL, t.hash(), Instant.now().plusSeconds(3600));
        when(emailTokens.findByTokenHash(t.hash())).thenReturn(Optional.of(token));
        when(emailTokens.consume(any(), any())).thenReturn(0);     // already consumed by a concurrent request

        ApiException ex = assertThrows(ApiException.class, () -> service.verifyEmail(t.raw(), CI));
        assertEquals("TOKEN_USED", ex.code());
        verify(users, never()).save(any());
    }

    @Test
    void verifyEmailRejectsExpiredAndUnknownTokens() {
        TokenService.IssuedToken expired = tokenService.issue();
        when(emailTokens.findByTokenHash(expired.hash())).thenReturn(Optional.of(
                new EmailToken(UUID.randomUUID(), TokenPurpose.VERIFY_EMAIL, expired.hash(), Instant.now().minusSeconds(5))));
        assertEquals("TOKEN_EXPIRED", assertThrows(ApiException.class, () -> service.verifyEmail(expired.raw(), CI)).code());

        assertEquals("TOKEN_INVALID", assertThrows(ApiException.class, () -> service.verifyEmail("not-a-real-token", CI)).code());
    }

    @Test
    void aPasswordResetTokenCannotBeUsedToVerifyEmail() {
        TokenService.IssuedToken t = tokenService.issue();
        when(emailTokens.findByTokenHash(t.hash())).thenReturn(Optional.of(
                new EmailToken(UUID.randomUUID(), TokenPurpose.RESET_PASSWORD, t.hash(), Instant.now().plusSeconds(60))));
        assertEquals("TOKEN_INVALID", assertThrows(ApiException.class, () -> service.verifyEmail(t.raw(), CI)).code());
    }

    // ---- login

    @Test
    void loginIsRefusedUntilTheEmailIsVerified() {
        User pending = new User(EMAIL, "hash", Role.USER, UserStatus.PENDING_VERIFICATION);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
        when(encoder.matches(STRONG, "hash")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.login(new LoginRequest(EMAIL, STRONG), Role.USER, CI));
        assertEquals("EMAIL_NOT_VERIFIED", ex.code());
        verify(sessions, never()).create(any(), any());
    }

    @Test
    void wrongPasswordIsGenericCountedAndAudited() {
        User active = new User(EMAIL, "hash", Role.USER, UserStatus.ACTIVE);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(active));
        when(encoder.matches(any(), any())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.login(new LoginRequest(EMAIL, "wrong-password!"), Role.USER, CI));

        assertEquals("INVALID_CREDENTIALS", ex.code());
        assertEquals(1, active.getFailedLoginAttempts());
        verify(audit).record(eq(Role.USER), eq("LOGIN_FAILED"), eq(false), any(), isNull(), any(), any());
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() {
        User active = new User(EMAIL, "hash", Role.USER, UserStatus.ACTIVE);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(active));
        when(encoder.matches(any(), any())).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> service.login(new LoginRequest(EMAIL, "nope-nope-nope"), Role.USER, CI));
        }
        assertNotNull(active.getLockedUntil());
        assertTrue(active.getLockedUntil().isAfter(Instant.now()));

        when(encoder.matches(STRONG, "hash")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.login(new LoginRequest(EMAIL, STRONG), Role.USER, CI));
        assertEquals("INVALID_CREDENTIALS", ex.code());
        verify(sessions, never()).create(any(), any());
    }

    @Test
    void anAdminCannotSignInThroughTheUserEndpointAndViceVersa() {
        User admin = new User("root@example.com", "hash", Role.ADMIN, UserStatus.ACTIVE);
        when(users.findByEmail("root@example.com")).thenReturn(Optional.of(admin));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("root@example.com", STRONG), Role.USER, CI));
        assertEquals("INVALID_CREDENTIALS", ex.code());
        verify(sessions, never()).create(any(), any());
    }

    // ---- password reset

    @Test
    void resettingAPasswordUpdatesTheHashAndRevokesEverySession() {
        UUID userId = UUID.randomUUID();
        TokenService.IssuedToken t = tokenService.issue();
        EmailToken token = new EmailToken(userId, TokenPurpose.RESET_PASSWORD, t.hash(), Instant.now().plusSeconds(600));
        User user = new User(EMAIL, "oldhash", Role.USER, UserStatus.ACTIVE);
        when(emailTokens.findByTokenHash(t.hash())).thenReturn(Optional.of(token));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(emailTokens.consume(any(), any())).thenReturn(1);
        when(encoder.encode("Fresh-Orbit-Passw0rd!")).thenReturn("newhash");

        service.resetPassword(new ResetPasswordRequest(t.raw(), "Fresh-Orbit-Passw0rd!"), CI);

        assertEquals("newhash", user.getPasswordHash());
        verify(sessions).revokeAll(any(), eq("PASSWORD_RESET"));
    }

    @Test
    void forgotPasswordIsSilentForUnknownAddresses() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.forgotPassword("ghost@example.com", CI));
        verifyNoInteractions(emailSender);
    }
}