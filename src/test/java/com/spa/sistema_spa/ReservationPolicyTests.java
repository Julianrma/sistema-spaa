package com.spa.sistema_spa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationPolicyTests {
    @Test
    void cancellationClosesExactlyAtStartAndRejectsTerminalStates() {
        var r = new Reservation(1L, 1, 1L, "Fixture", "fixture", "fixture", "fixture@example.com", LocalDate.of(2099, 1, 1), "10:00");
        assertTrue(ReservationPolicy.canCancel(r, LocalDateTime.of(2099, 1, 1, 9, 59)));
        assertFalse(ReservationPolicy.canCancel(r, LocalDateTime.of(2099, 1, 1, 10, 0)));
        assertFalse(ReservationPolicy.canCancel(r, LocalDateTime.of(2099, 1, 2, 9, 0)));
        for (String status : new String[]{"COMPLETADA", "CANCELADA", "UNKNOWN", null}) {
            r.setStatus(status);
            assertFalse(ReservationPolicy.canCancel(r, LocalDateTime.of(2099, 1, 1, 9, 0)));
        }
    }

    @Test
    void analyticsUnderstands24HourLegacyAndMalformedTimes() {
        var rows = java.util.Arrays.asList("09:00", "12:30", "18:00", "6:00 PM", "21:00", "invalid", null).stream()
                .map(time -> new Reservation(1L, 1, 1L, "Fixture", "fixture", "fixture", "fixture@example.com", LocalDate.of(2099, 1, 1), time)).toList();
        var analytics = DashboardAnalytics.from(rows, List.of());
        assertEquals(List.of(1, 1, 2, 1, 2), analytics.getTimeBandStats().stream().map(TimeBandStat::count).toList());
        assertEquals("Tarde (15:00 - 20:00)", analytics.getTimeBandStats().get(2).name());
    }
}
