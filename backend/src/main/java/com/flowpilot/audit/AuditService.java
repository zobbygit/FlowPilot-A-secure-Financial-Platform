package com.flowpilot.audit;

import com.flowpilot.common.ClientInfo;
import com.flowpilot.domain.AuditEvent;
import com.flowpilot.domain.Role;
import com.flowpilot.repo.AuditEventRepository;
import java.util.UUID;
import com.flowpilot.notify.LiveUpdateRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit rows in their own transaction so a failed login (which throws and rolls
 * back the caller) is still recorded. Never pass secrets, tokens or passwords in {@code details}.
 */
@Service
public class AuditService {
    private final AuditEventRepository repo;
    private final LiveUpdateRegistry live;

    public AuditService(AuditEventRepository repo, LiveUpdateRegistry live) {
        this.repo = repo;
        this.live = live;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Role role, String eventType, boolean success, UUID actorUserId, UUID sessionId,
                       ClientInfo ci, String details) {
        String scope = role == Role.ADMIN ? "ADMIN" : "USER";
        repo.save(new AuditEvent(scope, eventType, success ? "SUCCESS" : "FAILURE", actorUserId,
                role == null ? null : role.name(), sessionId, null, null,
                ci == null ? null : ci.ip(), ci == null ? null : ci.userAgent(), truncate(details)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { try { live.pushAdmins("timeline"); } catch (RuntimeException ignored) { /* hint only */ } }
            });
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 2000 ? s.substring(0, 2000) : s);
    }
}
