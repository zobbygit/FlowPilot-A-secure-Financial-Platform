package com.flowpilot.finance;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Typed, parameterised filters (no string-built SQL, no null-parameter typing pitfalls). */
final class TransactionSpecs {
    private TransactionSpecs() {}

    static Specification<FinanceTransaction> filter(UUID userId, LocalDate from, LocalDate to, TransactionType type,
                                                    UUID categoryId, UUID accountId, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            p.add(cb.isNull(root.get("archivedAt")));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("occurredOn"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("occurredOn"), to));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (categoryId != null) p.add(cb.equal(root.get("categoryId"), categoryId));
            if (accountId != null) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                Root<LedgerEntry> e = sq.from(LedgerEntry.class);
                sq.select(e.<UUID>get("transactionId")).where(cb.equal(e.get("accountId"), accountId));
                p.add(root.get("id").in(sq));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.<String>get("description")), like, '\\'),
                            cb.like(cb.lower(root.<String>get("merchant")), like, '\\')));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
