package com.spa.sistema_spa;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookingAvailability {

    private static final int SLOT_INTERVAL_MINUTES = 30;
    private static final int DEFAULT_EXISTING_SERVICE_MINUTES = 60;
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern HOURS_PATTERN = Pattern.compile(
            "^\\s*(.*?)\\s*:\\s*(\\d{1,2})h(\\d{2})\\s*-\\s*(\\d{1,2})h(\\d{2})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS_PATTERN = Pattern.compile(
            "^\\s*(Lun|Mar|Mi[eé]r|Jue|Vie|S[aá]b|Dom)\\s*(?:-\\s*(Lun|Mar|Mi[eé]r|Jue|Vie|S[aá]b|Dom))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private BookingAvailability() {
    }

    public static List<String> availableSlots(String openingHours, DayOfWeek day,
                                               int durationMinutes, List<Reservation> reservations,
                                               Map<Long, Integer> serviceDurations) {
        Schedule schedule = parse(openingHours);
        if (schedule == null || !schedule.openDays().contains(day) || durationMinutes <= 0) {
            return List.of();
        }

        List<String> available = new ArrayList<>();
        for (LocalTime start = schedule.opensAt();
             !start.plusMinutes(durationMinutes).isAfter(schedule.closesAt());
             start = start.plusMinutes(SLOT_INTERVAL_MINUTES)) {
            LocalTime slotStart = start;
            LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
            if (reservations.stream().noneMatch(reservation -> overlaps(
                slotStart, slotEnd, reservation, serviceDurations))) {
            available.add(slotStart.format(SLOT_FORMAT));
            }
        }
        return available;
    }

    private static boolean overlaps(LocalTime candidateStart, LocalTime candidateEnd,
                                    Reservation reservation, Map<Long, Integer> serviceDurations) {
        if ("CANCELADA".equals(reservation.getStatus())) {
            return false;
        }
        try {
            LocalTime existingStart = LocalTime.parse(reservation.getReservationTime(), SLOT_FORMAT);
            int existingDuration = serviceDurations.getOrDefault(
                    reservation.getServiceId(), DEFAULT_EXISTING_SERVICE_MINUTES);
            LocalTime existingEnd = existingStart.plusMinutes(existingDuration);
            return candidateStart.isBefore(existingEnd) && candidateEnd.isAfter(existingStart);
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private static Schedule parse(String openingHours) {
        if (openingHours == null) {
            return null;
        }
        Matcher hours = HOURS_PATTERN.matcher(openingHours);
        if (!hours.matches()) {
            return null;
        }

        Matcher days = DAYS_PATTERN.matcher(hours.group(1));
        if (!days.matches()) {
            return null;
        }

        try {
            LocalTime opensAt = LocalTime.of(Integer.parseInt(hours.group(2)), Integer.parseInt(hours.group(3)));
            LocalTime closesAt = LocalTime.of(Integer.parseInt(hours.group(4)), Integer.parseInt(hours.group(5)));
            if (!opensAt.isBefore(closesAt)) {
                return null;
            }
            return new Schedule(expandDays(dayOfWeek(days.group(1)), dayOfWeek(days.group(2))), opensAt, closesAt);
        } catch (DateTimeException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<DayOfWeek> expandDays(DayOfWeek first, DayOfWeek last) {
        List<DayOfWeek> days = new ArrayList<>();
        DayOfWeek current = first;
        days.add(current);
        while (last != null && current != last) {
            current = current.plus(1);
            days.add(current);
        }
        return days;
    }

    private static DayOfWeek dayOfWeek(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
        return switch (normalized) {
            case "lun" -> DayOfWeek.MONDAY;
            case "mar" -> DayOfWeek.TUESDAY;
            case "mier" -> DayOfWeek.WEDNESDAY;
            case "jue" -> DayOfWeek.THURSDAY;
            case "vie" -> DayOfWeek.FRIDAY;
            case "sab" -> DayOfWeek.SATURDAY;
            case "dom" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Día de la semana no reconocido");
        };
    }

    private record Schedule(List<DayOfWeek> openDays, LocalTime opensAt, LocalTime closesAt) {
    }
}