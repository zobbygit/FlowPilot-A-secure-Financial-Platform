package com.flowpilot.audit;

import com.flowpilot.security.AuthPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** A user's own financial audit trail. Read-only; the table itself is append-only at the database level. */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('USER')")
public class FinancialAuditController {
    public record Item(UUID id, String action, String entityType, UUID entityId, String summary, String previous, String next, String actor, Instant at) {}
    public record PageOf(List<Item> items, int page, int size, long total) {}

    private final JdbcTemplate jdbc;

    public FinancialAuditController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/financial")
    public PageOf list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String entityType, @RequestParam(required = false) String action,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        StringBuilder w = new StringBuilder(" where a.owner_id = ?"); List<Object> args = new ArrayList<>(List.of(p.userId()));
        if (entityType != null && entityType.matches("[A-Z_]{3,24}")) { w.append(" and a.entity_type = ?"); args.add(entityType); }
        if (action != null && action.matches("[A-Z_]{3,40}")) { w.append(" and a.action = ?"); args.add(action); }
        if (from != null) { w.append(" and a.created_at >= ?"); args.add(java.sql.Date.valueOf(from)); }
        if (to != null) { w.append(" and a.created_at < ?"); args.add(java.sql.Date.valueOf(to.plusDays(1))); }
        Long total = jdbc.queryForObject("select count(*) from financial_audit a" + w, Long.class, args.toArray());
        List<Object> pa = new ArrayList<>(args); pa.add(sz); pa.add((long) pg * sz);
        List<Item> items = jdbc.query("select a.*, up.full_name as actor_name from financial_audit a join user_profiles up on up.user_id = a.actor_id" + w + " order by a.created_at desc limit ? offset ?",
                (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), rs.getString("summary"), rs.getString("prev_value"), rs.getString("new_value"), rs.getString("actor_name"), rs.getTimestamp("created_at").toInstant()), pa.toArray());
        return new PageOf(items, pg, sz, total == null ? 0 : total);
    }

    @GetMapping("/financial/types")
    public List<String> types(@AuthenticationPrincipal AuthPrincipal p) { return jdbc.queryForList("select distinct entity_type from financial_audit where owner_id = ? order by 1", String.class, p.userId()); }
}
