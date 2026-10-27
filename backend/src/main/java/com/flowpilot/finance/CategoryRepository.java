package com.flowpilot.finance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    /** For pickers: active categories only. */
    @Query("select c from Category c where (c.userId is null or c.userId = :userId) and c.archivedAt is null order by c.kind, c.sortOrder, c.name")
    List<Category> findVisibleTo(@Param("userId") UUID userId);

    /** For labelling history: archived categories keep their name on old transactions. */
    @Query("select c from Category c where c.userId is null or c.userId = :userId")
    List<Category> findAllVisibleTo(@Param("userId") UUID userId);
}
