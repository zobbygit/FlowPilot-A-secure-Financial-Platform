package com.flowpilot.notify;

import com.flowpilot.repo.AuthSessionRepository;
import com.flowpilot.security.AuthPrincipal;
import java.io.IOException;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Server-to-client only. The handshake is authenticated by the same session cookie as REST; inbound data is ignored. */
@Component
public class LiveUpdateHandler extends TextWebSocketHandler {
    private final LiveUpdateRegistry registry;
    private final AuthSessionRepository sessions;

    public LiveUpdateHandler(LiveUpdateRegistry registry, AuthSessionRepository sessions) {
        this.registry = registry;
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (session.getPrincipal() instanceof Authentication a && a.getPrincipal() instanceof AuthPrincipal p) {
            if (!registry.register(p.userId(), p.sessionId(), p.role(), session)) session.close(CloseStatus.POLICY_VIOLATION);
        } else {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (message.getPayloadLength() > 256) session.close(CloseStatus.TOO_BIG_TO_PROCESS);   // clients have nothing to send
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    /** A revoked or expired login must not keep receiving live updates. */
    @Scheduled(fixedDelay = 60_000)
    public void closeRevokedConnections() {
        Instant now = Instant.now();
        for (LiveUpdateRegistry.Connection c : registry.all()) {
            boolean dead = sessions.findById(c.authSessionId()).map(s -> s.getRevokedAt() != null || s.getExpiresAt().isBefore(now)).orElse(true);
            if (dead) {
                try { c.socket().close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { /* already closed */ }
                registry.unregister(c.socket());
            }
        }
    }
}