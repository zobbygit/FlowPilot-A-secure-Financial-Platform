package com.flowpilot.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, UUID>, JpaSpecificationExecutor<FinanceTransaction> {
    Optional<FinanceTransaction> findByIdAndUserId(UUID id, UUID userId);
}
