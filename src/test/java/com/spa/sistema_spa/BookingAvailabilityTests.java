package com.spa.sistema_spa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class BookingAvailabilityTests {

    @Test
    void generatesNormalSlotsIncludingAnAppointmentEndingExactlyAtClosing() {
        assertEquals(List.of("09:00", "09:30", "10:00", "10:30", "11:00"),
                slots("Lun - Vie: 09h00 - 12h00", 60));
    }

    @Test
    void supportsSingleDayWithoutOpeningOtherDays() {
        assertEquals(List.of("09:00"), slots("Lun: 09h00 - 18h00", 540));
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun: 09h00 - 18h00", DayOfWeek.TUESDAY, 60, List.of(), Map.of()));
    }

    @Test
    void supportsAccentsAndDayRangesAcrossSunday() {
        for (DayOfWeek day : List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)) {
            assertEquals(List.of("09:00"), BookingAvailability.availableSlots(
                    "Sáb - Lun: 09h00 - 10h00", day, 60, List.of(), Map.of()));
        }
        assertEquals(List.of("09:00"), BookingAvailability.availableSlots(
                "Miér: 09h00 - 10h00", DayOfWeek.WEDNESDAY, 60, List.of(), Map.of()));
    }

    @Test
    void terminatesForTheFormerInfiniteLoopAndKeepsEverySlotInsideSchedule() {
        List<String> actual = slots("Lun - Vie: 08h00 - 23h59", 60);
        assertEquals(30, actual.size());
        assertEquals("08:00", actual.getFirst());
        assertEquals("22:30", actual.getLast());
        assertEquals(actual.size(), new HashSet<>(actual).size());
        for (int i = 0; i < actual.size(); i++) {
            int start = LocalTime.parse(actual.get(i)).toSecondOfDay() / 60;
            assertEquals(480 + i * 30, start);
            assertTrue(start + 60 <= 1439);
        }
    }

    @Test
    void handlesMinutesNearMidnightWithoutWrapping() {
        assertEquals(List.of("23:00", "23:30"), slots("Lun: 23h00 - 23h59", 29));
        assertEquals(List.of("23:00"), slots("Lun: 23h00 - 23h59", 59));
        assertEquals(List.of(), slots("Lun: 23h00 - 23h59", 60));
    }

    @Test
    void excludesStartsWhoseFullDurationWouldExceedClosing() {
        assertEquals(List.of("09:00", "09:30"), slots("Lun: 09h00 - 11h00", 61));
        assertEquals(List.of(), slots("Lun: 09h00 - 11h00", 121));
    }

    @Test
    void rejectsZeroDuration() {
        assertEquals(List.of(), slots("Lun - Vie: 08h00 - 23h59", 0));
    }

    @Test
    void rejectsNegativeDurations() {
        for (int duration : new int[] {-1, Integer.MIN_VALUE}) {
            assertEquals(List.of(), slots("Lun - Vie: 08h00 - 23h59", duration));
        }
    }

    @Test
    void rejectsExtremeAndDayWrappingDurations() {
        for (int duration : new int[] {1440, 1500, Integer.MAX_VALUE}) {
            assertEquals(List.of(), slots("Lun - Vie: 08h00 - 23h59", duration));
        }
    }

    @Test
    void rejectsMalformedClosedAndOvernightSchedules() {
        for (String schedule : Arrays.asList(null, "", "invalid", "Lun: 24h00 - 25h00",
                "Lun: 09h60 - 18h00", "Foo: 09h00 - 18h00", "Lun: 09h00 - 09h00",
                "Lun: 23h00 - 02h00")) {
            assertEquals(List.of(), slots(schedule, 60), String.valueOf(schedule));
        }
    }

    @Test
    void rejectsMissingInputs() {
        String schedule = "Lun: 09h00 - 18h00";
        assertEquals(List.of(), BookingAvailability.availableSlots(schedule, null, 60, List.of(), Map.of()));
        assertEquals(List.of(), BookingAvailability.availableSlots(schedule, DayOfWeek.MONDAY, 60, null, Map.of()));
        assertEquals(List.of(), BookingAvailability.availableSlots(schedule, DayOfWeek.MONDAY, 60, List.of(), null));
    }

    @Test
    void comparesFullIntervalsAndAllowsAdjacentAppointments() {
        assertEquals(List.of("09:00", "11:00"), BookingAvailability.availableSlots(
                "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 60,
                List.of(reservation("10:00")), Map.of(1L, 60)));
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 180,
                List.of(reservation("10:00")), Map.of(1L, 30)));
    }

    @Test
    void returnsNoSlotsWhenExistingReservationOccupiesWholeSchedule() {
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 30,
                List.of(reservation("09:00")), Map.of(1L, 180)));
    }

    @Test
    void retainsFallbackDurationForMissingService() {
        assertEquals(List.of("10:00", "10:30", "11:00"), BookingAvailability.availableSlots(
                "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 60,
                List.of(reservation("09:00")), Map.of()));
    }

    @Test
    void blocksAvailabilityForMalformedActiveReservationTimes() {
        for (String time : Arrays.asList(null, "invalid", "24:00", "09:60")) {
            assertEquals(List.of(), BookingAvailability.availableSlots(
                    "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 60,
                    List.of(reservation(time)), Map.of(1L, 60)));
        }
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun: 09h00 - 12h00", DayOfWeek.MONDAY, 60,
                Arrays.asList((Reservation) null), Map.of()));
    }

    @Test
    void blocksInvalidExistingDurationsWithoutOverflow() {
        for (Integer duration : Arrays.asList(null, 0, -1, 61, Integer.MAX_VALUE)) {
            Map<Long, Integer> durations = new HashMap<>();
            durations.put(1L, duration);
            assertEquals(List.of(), BookingAvailability.availableSlots(
                    "Lun: 23h00 - 23h59", DayOfWeek.MONDAY, 10,
                    List.of(reservation("23:00")), durations));
        }
    }

    @Test
    void detectsExistingIntervalEndingAtMidnightWithoutWrapping() {
        assertEquals(List.of(), BookingAvailability.availableSlots(
                "Lun: 23h00 - 23h59", DayOfWeek.MONDAY, 10,
                List.of(reservation("23:00")), Map.of(1L, 60)));
    }

    private List<String> slots(String schedule, int duration) {
        return BookingAvailability.availableSlots(schedule, DayOfWeek.MONDAY, duration, List.of(), Map.of());
    }

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
