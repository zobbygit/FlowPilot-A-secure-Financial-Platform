package com.flowpilot.automation;

import java.math.BigDecimal;
import java.util.UUID;

/** Published by the ledger after an income posting; automation reacts after the commit. */
public record IncomePosted(UUID userId, UUID transactionId, UUID accountId, UUID categoryId, BigDecimal amount, String currency) {}
