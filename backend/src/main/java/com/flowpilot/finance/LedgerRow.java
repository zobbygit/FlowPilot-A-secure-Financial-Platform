package com.flowpilot.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Projection used by dashboard aggregation. */
public record LedgerRow(LocalDate occurredOn, TransactionType type, BigDecimal amount, UUID categoryId) {}
