package com.flowpilot.notify;

import com.flowpilot.notify.NotificationService.*;
import com.flowpilot.security.AuthPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public PageOf list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(defaultValue = "false") boolean unreadOnly,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(p.userId(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthPrincipal p) { return Map.of("unread", service.unread(p.userId())); }

    @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.markRead(p.userId(), id); }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthPrincipal p) { return Map.of("updated", service.markAllRead(p.userId())); }

    @GetMapping("/preferences") public Prefs prefs(@AuthenticationPrincipal AuthPrincipal p) { return service.prefs(p.userId()); }
    @PutMapping("/preferences") public Prefs savePrefs(@AuthenticationPrincipal AuthPrincipal p, @RequestBody Prefs body) { return service.savePrefs(p.userId(), body); }
}
