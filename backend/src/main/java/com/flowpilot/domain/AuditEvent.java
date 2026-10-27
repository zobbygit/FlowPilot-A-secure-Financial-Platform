package com.flowpilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only (DB trigger forbids UPDATE/DELETE). All columns are insert-only. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, updatable = false) private String scope;
    @Column(name = "event_type", nullable = false, updatable = false) private String eventType;
    @Column(nullable = false, updatable = false) private String outcome;
    @Column(name = "actor_user_id", updatable = false) private UUID actorUserId;
    @Column(name = "actor_role", updatable = false) private String actorRole;
    @Column(name = "session_id", updatable = false) private UUID sessionId;
    @Column(name = "target_type", updatable = false) private String targetType;
    @Column(name = "target_id", updatable = false) private String targetId;
    @Column(name = "ip_address", updatable = false) private String ipAddress;
    @Column(name = "user_agent", updatable = false) private String userAgent;
    @Column(updatable = false) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected AuditEvent() {}

    public AuditEvent(String scope, String eventType, String outcome, UUID actorUserId, String actorRole,
                      UUID sessionId, String targetType, String targetId, String ip, String ua, String details) {
        this.scope = scope; this.eventType = eventType; this.outcome = outcome;
        this.actorUserId = actorUserId; this.actorRole = actorRole; this.sessionId = sessionId;
        this.targetType = targetType; this.targetId = targetId; this.ipAddress = ip; this.userAgent = ua;
        this.details = details;
    }

    public UUID getId() { return id; }
    public String getScope() { return scope; }
    public String getEventType() { return eventType; }
    public String getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
