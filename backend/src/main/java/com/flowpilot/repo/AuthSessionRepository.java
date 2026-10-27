package com.flowpilot.repo;

import com.flowpilot.domain.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByTokenHash(String tokenHash);

    List<AuthSession> findTop20ByUserIdOrderByLastActiveAtDesc(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession s set s.lastActiveAt = :now where s.id = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason "
            + "where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason "
            + "where s.userId = :userId and s.revokedAt is null and s.id <> :exceptId")
    int revokeAllExcept(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId,
                        @Param("reason") String reason, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :cutoff or (s.revokedAt is not null and s.revokedAt < :cutoff)")
    int deleteDeadBefore(@Param("cutoff") Instant cutoff);
}