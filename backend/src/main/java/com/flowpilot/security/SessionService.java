package com.flowpilot.security;

import com.flowpilot.audit.AuditService;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.config.AppProperties;
import com.flowpilot.domain.AuthSession;
import com.flowpilot.domain.Role;
import com.flowpilot.notify.NotificationService;
import com.flowpilot.repo.AuthSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    public record Created(AuthSession session, String rawToken) {}

    private final AuthSessionRepository sessions;
    private final TokenService tokens;
    private final AuditService audit;
    private final AppProperties props;
    private final NotificationService notifications;

    public SessionService(AuthSessionRepository sessions, TokenService tokens, AuditService audit, AppProperties props, NotificationService notifications) {
        this.notifications = notifications;
        this.sessions = sessions;
        this.tokens = tokens;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public Created create(UUID userId, ClientInfo ci) {
        TokenService.IssuedToken t = tokens.issue();
        Instant expires = Instant.now().plus(props.sessions().absoluteTimeout());
        List<AuthSession> previous = sessions.findTop20ByUserIdOrderByLastActiveAtDesc(userId);
        AuthSession s = sessions.save(new AuthSession(userId, t.hash(), expires, ci.userAgent(), ci.ip()));
        // Security alert: a sign-in from a browser string never seen before (only once the account has history).
        boolean unfamiliar = !previous.isEmpty() && previous.stream().noneMatch(x -> java.util.Objects.equals(x.getUserAgent(), ci.userAgent()));
        if (unfamiliar) notifications.notify(userId, "SECURITY", "New sign-in to your account", "A sign-in from a browser we haven't seen before. If this wasn't you, change your password and sign out other devices.",
                "/app/settings", "newdevice:" + s.getId());
        return new Created(s, t.raw());
    }

    public List<AuthSession> list(UUID userId) {
        return sessions.findTop20ByUserIdOrderByLastActiveAtDesc(userId);
    }

    /** Ownership is enforced here: another user's session id yields 404, never its details. */
    @Transactional
    public void revoke(UUID sessionId, UUID ownerId, Role role, String reason, ClientInfo ci) {
        AuthSession s = sessions.findById(sessionId)
                .filter(x -> x.getUserId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        if (s.getRevokedAt() == null) {
            s.revoke(reason, Instant.now());
            sessions.save(s);
            audit.record(role, "SESSION_REVOKED", true, ownerId, sessionId, ci, "reason=" + reason);
        }
    }

    @Transactional
    public int revokeOthers(UUID userId, UUID currentSessionId, String reason) {
        return sessions.revokeAllExcept(userId, currentSessionId, reason, Instant.now());
    }

    @Transactional
    public int revokeAll(UUID userId, String reason) {
        return sessions.revokeAllForUser(userId, reason, Instant.now());
    }

    @Transactional
    public void expire(UUID sessionId, Role role) {
        sessions.findById(sessionId).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.revoke("EXPIRED", Instant.now());
                sessions.save(s);
                audit.record(role, "SESSION_EXPIRED", true, s.getUserId(), s.getId(), null, "idle_or_absolute_timeout");
            }
        });
    }

    @Transactional
    public void touch(UUID sessionId) {
        sessions.touch(sessionId, Instant.now());
    }
}
