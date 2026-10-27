package com.flowpilot.reconciliation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReconciliationDtos {
    private ReconciliationDtos() {}

    public record Preview(List<String> headers, List<List<String>> sample, int rowCount, GuessedMapping guess, boolean looksLikeHeader) {}
    public record GuessedMapping(Integer dateCol, Integer descCol, Integer amountCol, Integer debitCol, Integer creditCol, Integer refCol, String dateFormat, String decimal) {}

    public record ImportSummary(UUID id, UUID accountId, String accountName, String fileName, int rowCount, int invalid, int suggested, int unmatched, int possibleDuplicates, int confirmed, int rejected,
                                Instant createdAt, String status) {}
    public record RowView(UUID id, int rowNo, LocalDate date, String amount, String description, String reference, String status, String validationError, Integer score, String note,
                          UUID suggestedTransactionId, String suggestedLabel, UUID matchedTransactionId) {}
    public record RowPage(ImportSummary summary, List<RowView> rows, int page, int size, long total, List<MissingView> missingInStatement) {}
    /** A recorded transaction in the statement period that no statement line accounts for. */
    public record MissingView(UUID id, LocalDate date, String label, String amount) {}
    public record ConfirmRequest(UUID transactionId) {}
}
