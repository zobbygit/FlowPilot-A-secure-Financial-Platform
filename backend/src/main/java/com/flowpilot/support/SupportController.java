package com.flowpilot.support;

import com.flowpilot.common.ClientInfo;
import com.flowpilot.files.FileStorageService.Content;
import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.support.SupportDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** User side (/api/support) and admin side (/api/admin/support). The service re-checks ownership/role on every call. */
@RestController
public class SupportController {
    private final SupportService service;

    public SupportController(SupportService service) { this.service = service; }

    // ---------------- user
    @PostMapping("/api/support/tickets") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('USER')")
    public TicketView create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CreateTicket r) { return service.create(p.userId(), r); }

    @GetMapping("/api/support/tickets") @PreAuthorize("hasRole('USER')")
    public PageOf<TicketView> mine(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.mine(p.userId(), status, page, size); }

    @GetMapping("/api/support/unread") @PreAuthorize("hasRole('USER')")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthPrincipal p) { return Map.of("unread", service.unread(p.userId(), com.flowpilot.domain.Role.USER)); }

    @GetMapping("/api/support/tickets/{id}") @PreAuthorize("hasRole('USER')")
    public Detail detail(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.detail(p, id); }

    @PostMapping(value = "/api/support/tickets/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('USER')")
    public Detail post(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(required = false) String body, @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest http) { return service.post(p, id, body, file, ClientInfo.from(http)); }

    @PostMapping("/api/support/tickets/{id}/reopen") @PreAuthorize("hasRole('USER')")
    public TicketView reopen(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.reopen(p.userId(), id); }

    @PostMapping("/api/support/tickets/{id}/close") @PreAuthorize("hasRole('USER')")
    public TicketView close(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.userClose(p.userId(), id); }

    @GetMapping("/api/support/messages/{id}/file") @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) { return serve(service.attachment(p, id), download); }

    // ---------------- admin
    @GetMapping("/api/admin/support/tickets") @PreAuthorize("hasRole('ADMIN')")
    public PageOf<TicketView> queue(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String status, @RequestParam(required = false) String priority, @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean mine, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.queue(p.userId(), status, priority, category, q, mine, page, size); }

    @GetMapping("/api/admin/support/unread") @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> adminUnread(@AuthenticationPrincipal AuthPrincipal p) { return Map.of("unread", service.unread(p.userId(), com.flowpilot.domain.Role.ADMIN)); }

    @GetMapping("/api/admin/support/tickets/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Detail adminDetail(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.detail(p, id); }

    @PostMapping(value = "/api/admin/support/tickets/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Detail adminPost(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(required = false) String body, @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest http) { return service.post(p, id, body, file, ClientInfo.from(http)); }

    @PostMapping("/api/admin/support/tickets/{id}/status") @PreAuthorize("hasRole('ADMIN')")
    public TicketView status(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody StatusRequest r, HttpServletRequest http) { return service.setStatus(p, id, r.status(), r.note(), ClientInfo.from(http)); }

    @PostMapping("/api/admin/support/tickets/{id}/priority") @PreAuthorize("hasRole('ADMIN')")
    public TicketView priority(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody PriorityRequest r, HttpServletRequest http) { return service.setPriority(p, id, r.priority(), ClientInfo.from(http)); }

    @PostMapping("/api/admin/support/tickets/{id}/assign") @PreAuthorize("hasRole('ADMIN')")
    public TicketView assign(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { return service.assignToMe(p, id, ClientInfo.from(http)); }

    @GetMapping("/api/admin/support/messages/{id}/file") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminFile(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) { return serve(service.attachment(p, id), download); }

    private static ResponseEntity<byte[]> serve(Content c, boolean download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mime())).header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + c.originalName().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; sandbox").header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(c.bytes());
    }
}
