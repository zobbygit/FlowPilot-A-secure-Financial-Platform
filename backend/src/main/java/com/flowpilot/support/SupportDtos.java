package com.flowpilot.support;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupportDtos {
    private SupportDtos() {}

    public record CreateTicket(@NotBlank @Size(max = 120) String title, @NotBlank @Pattern(regexp = "PAYMENT|RECORD|BUG|ACCOUNT|OTHER") String category,
                               @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority, @NotBlank @Size(max = 4000) String description, UUID paymentId) {}
    public record StatusRequest(@NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|AWAITING_USER|RESOLVED|CLOSED") String status, @Size(max = 300) String note) {}
    public record PriorityRequest(@NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority) {}

    public record TicketView(UUID id, String ticketNo, String title, String category, String priority, String description, String status, String paymentReference, String requesterName,
                             String requesterEmail, String assignedTo, Instant createdAt, Instant updatedAt, long unread, boolean canReopen, boolean canClose, boolean chatOpen) {}
    public record MessageView(UUID id, String senderRole, String senderName, String body, UUID fileMessageId, String fileName, String fileMime, Instant createdAt, Instant readAt, boolean mine) {}
    public record EventView(String actorRole, String actorName, String type, String from, String to, String note, Instant createdAt) {}
    public record Detail(TicketView ticket, List<MessageView> messages, List<EventView> events) {}
    public record PageOf<T>(List<T> items, int page, int size, long total) {}
}
