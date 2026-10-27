package com.flowpilot.notify;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Tracks live connections per user. The socket only carries tiny "something changed" hints;
 * the browser then re-reads authoritative state from the REST API (PostgreSQL stays the source of truth).
 */
@Component
public class LiveUpdateRegistry {
    private static final Logger log = LoggerFactory.getLogger(LiveUpdateRegistry.class);

    public record Connection(UUID userId, UUID authSessionId, com.flowpilot.domain.Role role, WebSocketSession socket) {}

    private final Map<String, Connection> byWsId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> byUser = new ConcurrentHashMap<>();

    public static final int MAX_CONNECTIONS_PER_USER = 6;

    /** Returns false (and registers nothing) when the user already has too many open sockets. */
    public boolean register(UUID userId, UUID authSessionId, com.flowpilot.domain.Role role, WebSocketSession raw) {
        Set<String> existing = byUser.get(userId);
        if (existing != null && existing.size() >= MAX_CONNECTIONS_PER_USER) return false;
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 64 * 1024);
        byWsId.put(raw.getId(), new Connection(userId, authSessionId, role, safe));
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(raw.getId());
        return true;
    }

    public void unregister(WebSocketSession raw) {
        Connection c = byWsId.remove(raw.getId());
        if (c != null) byUser.computeIfPresent(c.userId(), (k, v) -> { v.remove(raw.getId()); return v.isEmpty() ? null : v; });
    }

    public Iterable<Connection> all() { return byWsId.values(); }

    public int connectionCount() { return byWsId.size(); }

    /** Hint to every connected user (announcements). */
    public void pushEveryone(String topic) {
        byUser.keySet().forEach(u -> push(u, topic));
    }

    /** Same hint, to every connected administrator (support queue, timeline). */
    public void pushAdmins(String topic) {
        byWsId.values().stream().filter(c -> c.role() == com.flowpilot.domain.Role.ADMIN).map(Connection::userId).distinct().forEach(u -> push(u, topic));
    }

    /** Hint only: {"type":"refresh","topic":"notifications"}. Never put financial data in the payload. */
    public void push(UUID userId, String topic) {
        Set<String> ids = byUser.get(userId);
        if (ids == null) return;
        String json = "{\"type\":\"refresh\",\"topic\":\"" + topic.replaceAll("[^a-z_]", "") + "\"}";
        for (String id : ids) {
            Connection c = byWsId.get(id);
            if (c == null) continue;
            try {
                c.socket().sendMessage(new TextMessage(json));
            } catch (IOException | RuntimeException e) {
                log.debug("Dropping dead live connection");
                try { c.socket().close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { /* already gone */ }
                unregister(c.socket());
            }
        }
    }
}