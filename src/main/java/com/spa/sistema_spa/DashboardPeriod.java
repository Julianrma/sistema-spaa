package com.spa.sistema_spa;

import java.time.LocalDate;
import java.util.List;

/** Windows refer to appointment dates, not booking creation dates (not stored). */
public record DashboardPeriod(String key, String label, LocalDate start, LocalDate end) {
    public static DashboardPeriod of(String value, LocalDate today) {
        return switch (value == null ? "all" : value) {
            case "today" -> new DashboardPeriod("today", "Hoy", today, today);
            case "7d" -> new DashboardPeriod("7d", "Últimos 7 días", today.minusDays(6), today);
            case "30d" -> new DashboardPeriod("30d", "Últimos 30 días", today.minusDays(29), today);
            default -> new DashboardPeriod("all", "Histórico", null, null);
        };
    }

    public List<Reservation> select(List<Reservation> reservations) {
        if (start == null) return reservations;
        return reservations.stream().filter(r -> r.getReservationDate() != null
                && !r.getReservationDate().isBefore(start) && !r.getReservationDate().isAfter(end)).toList();
    }
}
