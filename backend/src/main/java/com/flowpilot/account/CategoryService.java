package com.flowpilot.account;

import com.flowpilot.account.AccountDtos.*;
import com.flowpilot.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archiving never rewrites history: old transactions keep their category and its name. */
@Service
public class CategoryService {
    private final JdbcTemplate jdbc;

    public CategoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CategoryManageView> list(UUID userId) {
        return jdbc.query("select c.id, c.name, c.kind, c.color, c.sort_order, c.archived_at, c.user_id, "
                + "(select count(*) from transactions t where t.category_id = c.id and t.user_id = ?) as usage "
                + "from categories c where c.user_id is null or c.user_id = ? order by c.kind, c.sort_order, c.name",
                (rs, i) -> new CategoryManageView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getString("color"), rs.getInt("sort_order"),
                        rs.getTimestamp("archived_at") != null, rs.getObject("user_id") == null, rs.getLong("usage")), userId, userId);
    }

    @Transactional
    public CategoryManageView create(UUID userId, CategoryRequest r) {
        UUID id = jdbc.queryForObject("insert into categories (user_id, name, kind, color, sort_order) values (?, ?, ?, ?, "
                + "coalesce((select max(sort_order) + 10 from categories where user_id = ? and kind = ?), 100)) returning id", UUID.class,
                userId, r.name().trim(), r.kind(), r.color(), userId, r.kind());
        return list(userId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public CategoryManageView update(UUID userId, UUID id, CategoryRequest r) {
        // kind is immutable once created: changing it would silently reinterpret historical transactions
        if (jdbc.update("update categories set name = ?, color = ? where id = ? and user_id = ? and kind = ?", r.name().trim(), r.color(), id, userId, r.kind()) == 0) {
            throw ApiException.notFound("Category not found (built-in categories and changing the kind are not editable).");
        }
        return list(userId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void setArchived(UUID userId, UUID id, boolean archived) {
        if (jdbc.update("update categories set archived_at = " + (archived ? "now()" : "null") + " where id = ? and user_id = ?", id, userId) == 0) {
            throw ApiException.notFound("Category not found.");
        }
    }

    @Transactional
    public void reorder(UUID userId, List<UUID> ids) {
        int order = 10;
        for (UUID id : ids) {
            if (jdbc.update("update categories set sort_order = ? where id = ? and user_id = ?", order, id, userId) == 0) throw ApiException.notFound("Category not found.");
            order += 10;
        }
    }
}
