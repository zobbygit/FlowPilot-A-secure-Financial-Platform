package com.flowpilot.reports;

import com.flowpilot.common.ApiException;
import com.flowpilot.common.RateLimiter;
import com.flowpilot.reports.CalendarService.Event;
import com.flowpilot.reports.ReportService.ReportTable;
import com.flowpilot.security.AuthPrincipal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('USER')")
public class ReportController {
    private final ReportService reports;
    private final ExportService exports;
    private final CalendarService calendar;
    private final RateLimiter limiter;
    private final JdbcTemplate jdbc;
    private final PipelineService pipeline;

    public ReportController(ReportService reports, ExportService exports, CalendarService calendar, RateLimiter limiter, JdbcTemplate jdbc, PipelineService pipeline) {
        this.pipeline = pipeline; this.reports = reports; this.exports = exports; this.calendar = calendar; this.limiter = limiter; this.jdbc = jdbc;
    }

    @GetMapping("/reports/{type}")
    public ReportTable report(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency, @RequestParam(defaultValue = "30") int horizon) {
        return build(p, type, from, to, currency, horizon);
    }

    @GetMapping("/reports/{type}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String type, @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency, @RequestParam(defaultValue = "30") int horizon) {
        limiter.check("report-export", p.userId().toString(), 20, Duration.ofMinutes(10));
        ReportTable t = build(p, type, from, to, currency, horizon);
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"csv".equalsIgnoreCase(format)) throw ApiException.badRequest("INVALID_FORMAT", "Use csv or pdf.");
        String name = jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, p.userId());
        byte[] body = pdf ? exports.pdf(t, name) : exports.csv(t);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowpilot-" + type + (pdf ? ".pdf" : ".csv") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv; charset=utf-8")).body(body);
    }

    @GetMapping("/pipeline")
    public PipelineService.Pipeline pipeline(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String currency) {
        return pipeline.build(p.userId(), from, to, currency);
    }

    @GetMapping("/safe-to-spend")
    public PipelineService.SafeToSpend safeToSpend(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String currency) { return pipeline.safeToSpend(p.userId(), currency); }

    private static final List<String> WIDGETS = List.of("stats", "safe", "pipeline", "cashflow", "spending", "bills", "goals", "recent");
    public record Layout(List<String> widgets) {}

    /** Only presentation is stored: which widgets are visible and in what order. Unknown ids are rejected. */
    @GetMapping("/dashboard-layout")
    public Layout layout(@AuthenticationPrincipal AuthPrincipal p) {
        List<String> saved = jdbc.query("select widgets from dashboard_layouts where user_id = ?", (rs, i) -> rs.getString(1), p.userId());
        return new Layout(saved.isEmpty() ? WIDGETS : java.util.Arrays.stream(saved.get(0).split(",")).filter(WIDGETS::contains).toList());
    }

    @PutMapping("/dashboard-layout")
    public Layout saveLayout(@AuthenticationPrincipal AuthPrincipal p, @RequestBody Layout in) {
        List<String> w = in.widgets() == null ? List.of() : in.widgets().stream().distinct().toList();
        if (w.isEmpty() || w.size() > WIDGETS.size() || !WIDGETS.containsAll(w)) throw ApiException.badRequest("INVALID_LAYOUT", "Choose at least one of the available widgets.");
        jdbc.update("insert into dashboard_layouts (user_id, widgets) values (?, ?) on conflict (user_id) do update set widgets = excluded.widgets, updated_at = now()", p.userId(), String.join(",", w));
        return new Layout(w);
    }

    @DeleteMapping("/dashboard-layout")
    public Layout resetLayout(@AuthenticationPrincipal AuthPrincipal p) { jdbc.update("delete from dashboard_layouts where user_id = ?", p.userId()); return new Layout(WIDGETS); }

    @GetMapping("/calendar")
    public List<Event> calendar(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendar.events(p.userId(), from, to);
    }

    private ReportTable build(AuthPrincipal p, String type, LocalDate from, LocalDate to, String currency, int horizon) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate f = from == null ? today.withDayOfYear(1) : from, t = to == null ? today : to;
        UUID u = p.userId();
        String cur = currency != null && currency.matches("[A-Z]{3}") ? currency
                : jdbc.queryForObject("select preferred_currency from user_profiles where user_id = ?", String.class, u);
        return reports.build(u, type, f, t, cur, horizon);
    }
}
