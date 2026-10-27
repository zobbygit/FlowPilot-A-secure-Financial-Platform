package com.flowpilot.repo;

import com.flowpilot.domain.EmailToken;
import com.flowpilot.domain.TokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {
    Optional<EmailToken> findByTokenHash(String tokenHash);

    /** Atomic single-use consumption: returns 0 if another request already consumed or revoked it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailToken t set t.consumedAt = :now where t.id = :id and t.consumedAt is null and t.revokedAt is null")
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailToken t set t.revokedAt = :now where t.userId = :userId and t.purpose = :purpose "
            + "and t.consumedAt is null and t.revokedAt is null")
    int revokeOutstanding(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);

    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}