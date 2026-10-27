package com.flowpilot.payments;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentProfile(String handle, UUID receiveAccountId, boolean simulated) {}
    public record ReceiveAccountRequest(UUID accountId) {}
    public record Lookup(String handle, String displayName) {}

    public record InitiateRequest(
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,29}$") String toHandle,
            @Pattern(regexp = "^FP-[A-Z0-9]{10}$") String requestReference,
            @NotNull UUID fromAccountId,
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 200) String note) {}

    public record RequestCreate(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,29}$") String fromHandle,
            @Size(max = 200) String note) {}

    public record ReasonRequest(@Size(max = 500) String reason) {}

    public record RequestView(UUID id, String reference, String direction, String counterpartyName, String amount, String currency, String note, String status, Instant expiresAt, Instant createdAt) {}

    public record PaymentView(UUID id, String reference, String direction, String counterpartyName, String counterpartyHandle, String amount, String currency, String note, String status,
                              String failureReason, String requestReference, boolean canConfirm, boolean canCancel, boolean canRefund, boolean canDispute, String disputeStatus,
                              Instant createdAt, Instant updatedAt, boolean simulated) {}

    public record EventView(String fromStatus, String toStatus, String note, Instant createdAt) {}
    public record PaymentDetail(PaymentView payment, List<EventView> events, RefundView refund) {}
    public record RefundView(String amount, String reason, Instant createdAt) {}
    public record PageOf<T>(List<T> items, int page, int size, long total) {}
}
