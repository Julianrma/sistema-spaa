package com.spa.sistema_spa;

import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ReservationFilters {
    private ReservationFilters() { }
    public static Specification<Reservation> matching(String status, LocalDate date, Integer branch,
                                                       Long masseuse, String search) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null && !status.isBlank()) predicates.add(cb.equal(root.get("status"), status));
            if (date != null) predicates.add(cb.equal(root.get("reservationDate"), date));
            if (branch != null) predicates.add(cb.equal(root.get("branchId"), branch));
            if (masseuse != null) predicates.add(cb.equal(root.get("masseuseId"), masseuse));
            if (search != null && !search.isBlank()) {
                String term = search.strip().toLowerCase(Locale.ROOT);
                String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                var name = cb.like(cb.lower(root.get("customerName")), "%" + escaped + "%", '\\');
                try {
                    long id = Long.parseLong(term.replaceFirst("^smb-", ""));
                    predicates.add(cb.or(name, cb.equal(root.get("id"), id)));
                } catch (NumberFormatException ignored) { predicates.add(name); }
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
