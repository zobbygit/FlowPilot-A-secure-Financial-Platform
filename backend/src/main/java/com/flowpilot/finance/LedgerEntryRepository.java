package com.flowpilot.finance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);
    List<LedgerEntry> findByTransactionIdIn(Collection<UUID> transactionIds);

    /** Income/expense flows for reporting. Reversed originals and reversal rows are both excluded, so they net to zero. */
    @Query("select new com.flowpilot.finance.LedgerRow(t.occurredOn, t.type, e.amount, t.categoryId) "
            + "from LedgerEntry e join FinanceTransaction t on t.id = e.transactionId "
            + "where e.userId = :userId and e.currency = :currency and t.occurredOn >= :from and t.occurredOn <= :to "
            + "and t.status = com.flowpilot.finance.TxStatus.POSTED and t.reversesTransactionId is null "
            + "and t.archivedAt is null "
            + "and t.type in (com.flowpilot.finance.TransactionType.INCOME, com.flowpilot.finance.TransactionType.EXPENSE)")
    List<LedgerRow> flows(@Param("userId") UUID userId, @Param("currency") String currency,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Reconciliation check: must equal accounts.current_balance for every account. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.accountId = :accountId")
    java.math.BigDecimal sumForAccount(@Param("accountId") UUID accountId);
}
