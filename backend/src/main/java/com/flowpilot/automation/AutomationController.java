package com.flowpilot.automation;

import com.flowpilot.automation.AutomationDtos.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/automation")
@PreAuthorize("hasRole('USER')")
public class AutomationController {
    private final AutomationService service;

    public AutomationController(AutomationService service) { this.service = service; }

    @GetMapping("/rules") public List<RuleView> rules(@AuthenticationPrincipal AuthPrincipal p) { return service.rules(p.userId()); }
    @PostMapping("/rules") @ResponseStatus(HttpStatus.CREATED) public RuleView create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody RuleRequest r) { return service.create(p.userId(), r); }
    @PutMapping("/rules/{id}") public RuleView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody RuleRequest r) { return service.update(p.userId(), id, r); }
    @PostMapping("/rules/{id}/pause") @ResponseStatus(HttpStatus.NO_CONTENT) public void pause(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setStatus(p.userId(), id, "PAUSED"); }
    @PostMapping("/rules/{id}/resume") @ResponseStatus(HttpStatus.NO_CONTENT) public void resume(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setStatus(p.userId(), id, "ACTIVE"); }
    @PostMapping("/rules/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT) public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setStatus(p.userId(), id, "ARCHIVED"); }
    @PostMapping("/rules/{id}/preview") public Preview preview(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody PreviewRequest r) { return service.preview(p.userId(), id, r.incomeAmount()); }
    @PostMapping("/rules/{id}/run") @ResponseStatus(HttpStatus.CREATED) public RunView run(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody PreviewRequest r) { return service.runManual(p.userId(), id, r.incomeAmount()); }
    @GetMapping("/runs") public List<RunView> runs(@AuthenticationPrincipal AuthPrincipal p) { return service.runs(p.userId()); }
    @PostMapping("/runs/{id}/retry") @ResponseStatus(HttpStatus.CREATED) public RunView retry(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.retry(p.userId(), id); }
    @PostMapping("/allocations/{id}/release") @ResponseStatus(HttpStatus.NO_CONTENT) public void release(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.release(p.userId(), id); }
}

@Component
class AutomationJobs {
    private final AutomationService service;

    AutomationJobs(AutomationService service) { this.service = service; }

    /** Daily at 02:30 UTC. The unique (rule, trigger key) index makes a repeat or overlapping run a no-op. Missed days are NOT back-filled. */
    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    void daily() { service.runSchedules(java.time.LocalDate.now(java.time.ZoneOffset.UTC)); }
}
