package com.flowpilot.admin;

import com.flowpilot.admin.AdminDtos.*;
import com.flowpilot.common.ApiException;
import com.flowpilot.common.ClientInfo;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.reports.ExportService;
import com.flowpilot.reports.ReportService.ReportTable;
import com.flowpilot.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Everything under /api/admin requires ROLE_ADMIN, enforced by the security filter chain AND again here per method. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService admin;
    private final SettingsService settings;
    private final ExportService exports;
    private final RateLimiter limiter;
    private final JdbcTemplate jdbc;

    public AdminController(AdminService admin, SettingsService settings, ExportService exports, RateLimiter limiter, JdbcTemplate jdbc) {
        this.admin = admin; this.settings = settings; this.exports = exports; this.limiter = limiter; this.jdbc = jdbc;
    }

    @GetMapping("/dashboard") public Dashboard dashboard() { return admin.dashboard(); }

    // ---- users
    @GetMapping("/users") public UserPage users(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return admin.users(q, status, page, size); }
    @PostMapping("/users/{id}/suspend") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r, HttpServletRequest http) { admin.suspend(p, id, r.reason(), ClientInfo.from(http)); }
    @PostMapping("/users/{id}/reactivate") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r, HttpServletRequest http) { admin.reactivate(p, id, r.reason(), ClientInfo.from(http)); }

    // ---- payments
    @GetMapping("/payments")
    public PaymentPage payments(@RequestParam(required = false) String status, @RequestParam(required = false) String q, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) BigDecimal minAmount,
                                @RequestParam(defaultValue = "false") boolean suspicious, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return admin.payments(status, q, from, to, minAmount, suspicious, page, size); }
    @GetMapping("/payments/{id}") public Map<String, Object> payment(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { return admin.paymentDetail(p, id, ClientInfo.from(http)); }
    @PostMapping("/payments/{id}/refund") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r, HttpServletRequest http) { limiter.check("admin-refund", p.userId().toString(), 10, Duration.ofMinutes(10)); admin.refundPayment(p, id, r.reason(), ClientInfo.from(http)); }
    @PostMapping("/payments/{id}/dispute/reject") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r, HttpServletRequest http) { admin.rejectDispute(p, id, r.reason(), ClientInfo.from(http)); }

    // ---- audit & timeline
    @GetMapping("/audit")
    public AuditPage audit(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String scope, @RequestParam(required = false) String type, @RequestParam(required = false) String actor, @RequestParam(required = false) String outcome,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, HttpServletRequest http) { return admin.auditLog(p, scope, type, actor, outcome, from, to, page, size, false, ClientInfo.from(http)); }
    @GetMapping("/timeline")
    public AuditPage timeline(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size, HttpServletRequest http) { return admin.auditLog(p, null, null, null, null, null, null, page, size, true, ClientInfo.from(http)); }

    // ---- oversight
    @GetMapping("/oversight/categories") public List<DefaultCategory> categories() { return admin.defaultCategories(); }
    @PostMapping("/oversight/categories") @ResponseStatus(HttpStatus.CREATED)
    public DefaultCategory createCategory(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CategoryRequest r, HttpServletRequest http) { return admin.createDefaultCategory(p, r, ClientInfo.from(http)); }
    @PutMapping("/oversight/categories/{id}")
    public DefaultCategory renameCategory(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody CategoryRequest r, HttpServletRequest http) { return admin.renameDefaultCategory(p, id, r, ClientInfo.from(http)); }
    @PostMapping("/oversight/categories/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { admin.archiveDefaultCategory(p, id, true, ClientInfo.from(http)); }
    @PostMapping("/oversight/categories/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { admin.archiveDefaultCategory(p, id, false, ClientInfo.from(http)); }
    @PostMapping("/oversight/investigations") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> openInvestigation(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody InvestigationRequest r, HttpServletRequest http) { return Map.of("id", admin.openInvestigation(p, r, ClientInfo.from(http))); }
    @GetMapping("/oversight/investigations/{id}")
    public InvestigationSummary investigation(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { return admin.investigation(p, id, ClientInfo.from(http)); }

    // ---- announcements
    @GetMapping("/announcements") public List<AnnouncementView> announcements() { return admin.announcements(); }
    @PostMapping("/announcements") @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementView createAnnouncement(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody AnnouncementRequest r, HttpServletRequest http) { return admin.saveAnnouncement(p, null, r, ClientInfo.from(http)); }
    @PutMapping("/announcements/{id}")
    public AnnouncementView editAnnouncement(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody AnnouncementRequest r, HttpServletRequest http) { return admin.saveAnnouncement(p, id, r, ClientInfo.from(http)); }
    @PostMapping("/announcements/{id}/publish") public AnnouncementView publish(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { return admin.publish(p, id, ClientInfo.from(http)); }
    @DeleteMapping("/announcements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnouncement(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, HttpServletRequest http) { admin.deleteDraft(p, id, ClientInfo.from(http)); }

    // ---- reports
    @GetMapping("/reports/{type}")
    public ReportTable report(@PathVariable String type, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return admin.report(type, from, to); }

    @GetMapping("/reports/{type}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String type, @RequestParam String format, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        limiter.check("admin-report-export", p.userId().toString(), 20, Duration.ofMinutes(10));
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"csv".equalsIgnoreCase(format)) throw ApiException.badRequest("INVALID_FORMAT", "Use csv or pdf.");
        ReportTable t = admin.report(type, from, to);
        byte[] body = pdf ? exports.pdf(t, jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, p.userId())) : exports.csv(t);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowpilot-admin-" + type + (pdf ? ".pdf" : ".csv") + "\"").header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv; charset=utf-8")).body(body);
    }

    // ---- configuration (whitelisted keys only; security-critical settings are environment-only)
    @GetMapping("/settings") public List<SettingsService.Setting> settings() { return settings.all(); }
    @PutMapping("/settings/{key}")
    public SettingsService.Setting updateSetting(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String key, @Valid @RequestBody SettingRequest r, HttpServletRequest http) { return settings.update(p.userId(), key, r.value(), ClientInfo.from(http)); }
}
