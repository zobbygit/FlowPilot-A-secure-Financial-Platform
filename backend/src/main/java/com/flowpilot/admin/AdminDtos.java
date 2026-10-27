package com.flowpilot.admin;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record DayPoint(LocalDate day, long transactions, long payments, long newUsers) {}
    public record AuditItem(UUID id, String scope, String type, String outcome, String actorEmail, String actorRole, String ip, String details, Instant at) {}
    public record Health(long dbLatencyMs, int liveConnections, long heapUsedMb, long heapMaxMb, long uptimeMinutes, String schemaVersion, long staleBackgroundWork, List<String> checks) {}
    public record Dashboard(Map<String, Long> usersByStatus, long newUsers7d, long newUsers30d, long transactions24h, long transactions7d, Map<String, Long> paymentsByStatus, long failedPayments24h,
                            List<Map<String, String>> paymentVolume7d, long openDisputes, long refunds7d, long openTickets, long urgentOpenTickets, long unassignedTickets, Double avgFirstResponseHours,
                            Health health, List<AuditItem> recentAudit, List<DayPoint> activity) {}

    public record UserRow(UUID id, String email, String fullName, String status, Instant createdAt, boolean emailVerified, Instant lastLogin, long accounts, long openTickets) {}
    public record UserPage(List<UserRow> items, int page, int size, long total) {}
    public record ReasonRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}

    public record PaymentRow(UUID id, String reference, String payer, String payee, String amount, String currency, String status, String failureReason, Instant createdAt, List<String> flags, String disputeStatus) {}
    public record PaymentPage(List<PaymentRow> items, int page, int size, long total) {}

    public record AuditPage(List<AuditItem> items, int page, int size, long total) {}

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String kind, @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}
    public record DefaultCategory(UUID id, String name, String kind, String color, boolean archived, long usage) {}

    public record InvestigationRequest(@NotNull UUID userId, UUID ticketId, @NotBlank @Size(min = 20, max = 500) String reason) {}
    public record InvestigationSummary(UUID id, String userName, String userEmail, String status, Instant expiresAt, long accounts, Map<String, String> balancesByCurrency, long transactions90d, long payments90d,
                                       long openBills, long budgets, List<Map<String, String>> recentPayments, List<String> notes) {}

    public record AnnouncementRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 1000) String body, @NotBlank @Pattern(regexp = "ALL|NEW_USERS_30D|SPECIFIC") String audience, @Size(max = 2000) String targetEmails) {}
    public record AnnouncementView(UUID id, String title, String body, String audience, String status, Instant createdAt, Instant publishedAt, int delivered, long read, int targetCount) {}

    public record SettingRequest(@NotNull @Size(max = 500) String value) {}
}
