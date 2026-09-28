package com.spa.sistema_spa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BookingAvailabilityTests {

    @Test
    void respectsBranchDaysAndServiceDurationAtClosingTime() {
        List<String> slots = BookingAvailability.availableSlots(
                "Lun - Sáb: 08h30 - 20h00", DayOfWeek.MONDAY, 90, List.of(), Map.of());

        assertEquals("08:30", slots.get(0));
        assertEquals("18:30", slots.get(slots.size() - 1));
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun - Sáb: 08h30 - 20h00", DayOfWeek.SUNDAY, 90, List.of(), Map.of()));
    }

    @Test
    void excludesOverlappingReservationsButAllowsCancelledOnes() {
        Reservation active = reservation("09:00");
        Reservation cancelled = reservation("10:00");
        cancelled.setStatus("CANCELADA");

        List<String> slots = BookingAvailability.availableSlots(
                "Lun - Vie: 09h00 - 12h00", DayOfWeek.MONDAY, 60,
                List.of(active, cancelled), Map.of(1L, 60));

        assertEquals(List.of("10:00", "10:30", "11:00"), slots);
    }

    private Reservation reservation(String time) {
        return new Reservation(1L, 1, 1L, "Cliente", "0000000000", "0990000000",
                "cliente@example.com", LocalDate.of(2026, 9, 28), time);
    }
}