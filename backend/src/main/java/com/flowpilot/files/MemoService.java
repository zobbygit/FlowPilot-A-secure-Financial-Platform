package com.flowpilot.files;

import com.flowpilot.common.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoService {
    public record MemoRequest(@NotBlank @Size(max = 120) String title, @Size(max = 4000) String body, @Size(max = 300) String tags, boolean pinned,
                              LocalDate followUpDate, UUID transactionId, UUID goalId) {}
    public record MemoView(UUID id, String title, String body, List<String> tags, boolean pinned, LocalDate followUpDate, UUID transactionId, UUID goalId, Instant updatedAt) {}

    private final JdbcTemplate jdbc;

    public MemoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MemoView> list(UUID userId, String q, String tag, boolean pinnedOnly, UUID transactionId) {
        StringBuilder sql = new StringBuilder("select * from memos where user_id = ? and archived_at is null");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(userId));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" and (lower(title) like ? escape '\\' or lower(coalesce(body, '')) like ? escape '\\' or lower(coalesce(tags, '')) like ? escape '\\')");
            args.add(like); args.add(like); args.add(like);
        }
        if (tag != null && !tag.isBlank()) { sql.append(" and (',' || lower(coalesce(tags, '')) || ',') like ?"); args.add("%," + tag.trim().toLowerCase().replace("%", "").replace("_", "") + ",%"); }
        if (pinnedOnly) sql.append(" and pinned");
        if (transactionId != null) { sql.append(" and transaction_id = ?"); args.add(transactionId); }
        sql.append(" order by pinned desc, updated_at desc limit 200");
        return jdbc.query(sql.toString(), (rs, i) -> view(rs), args.toArray());
    }

    @Transactional
    public MemoView create(UUID userId, MemoRequest r) {
        refs(userId, r);
        UUID id = jdbc.queryForObject("insert into memos (user_id, title, body, tags, pinned, follow_up_date, transaction_id, goal_id) values (?, ?, ?, ?, ?, ?, ?, ?) returning id", UUID.class,
                userId, r.title().trim(), blank(r.body()), normTags(r.tags()), r.pinned(), r.followUpDate(), r.transactionId(), r.goalId());
        return get(userId, id);
    }

    @Transactional
    public MemoView update(UUID userId, UUID id, MemoRequest r) {
        refs(userId, r);
        if (jdbc.update("update memos set title = ?, body = ?, tags = ?, pinned = ?, follow_up_date = ?, transaction_id = ?, goal_id = ?, updated_at = now() where id = ? and user_id = ? and archived_at is null",
                r.title().trim(), blank(r.body()), normTags(r.tags()), r.pinned(), r.followUpDate(), r.transactionId(), r.goalId(), id, userId) == 0) throw ApiException.notFound("Memo not found.");
        return get(userId, id);
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        if (jdbc.update("update memos set archived_at = now() where id = ? and user_id = ? and archived_at is null", id, userId) == 0) throw ApiException.notFound("Memo not found.");
    }

    private MemoView get(UUID userId, UUID id) {
        return jdbc.query("select * from memos where id = ? and user_id = ?", (rs, i) -> view(rs), id, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Memo not found."));
    }

    private void refs(UUID userId, MemoRequest r) {
        if (r.transactionId() != null && jdbc.queryForObject("select count(*) from transactions where id = ? and user_id = ?", Integer.class, r.transactionId(), userId) == 0) throw ApiException.notFound("Transaction not found.");
        if (r.goalId() != null && jdbc.queryForObject("select count(*) from savings_goals where id = ? and user_id = ?", Integer.class, r.goalId(), userId) == 0) throw ApiException.notFound("Goal not found.");
    }

    private static MemoView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        String tags = rs.getString("tags");
        return new MemoView(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body"), tags == null || tags.isBlank() ? List.of() : List.of(tags.split(",")),
                rs.getBoolean("pinned"), rs.getObject("follow_up_date", LocalDate.class), rs.getObject("transaction_id", UUID.class), rs.getObject("goal_id", UUID.class), rs.getTimestamp("updated_at").toInstant());
    }

    static String normTags(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return java.util.Arrays.stream(raw.split(",")).map(t -> t.trim().toLowerCase().replaceAll("[^a-z0-9 _-]", "")).filter(t -> !t.isEmpty()).distinct().limit(8).collect(java.util.stream.Collectors.joining(","));
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
