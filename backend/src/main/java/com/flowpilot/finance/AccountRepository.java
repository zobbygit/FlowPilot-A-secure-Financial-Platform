package com.flowpilot.finance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID userId);

    /** Row lock: serialises concurrent balance changes on the same account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.userId = :userId and a.archivedAt is null")
    Optional<Account> findActiveForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
}
