package com.spa.sistema_spa;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class ReservationPolicy {
    public static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    public static final List<String> STATES = List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA", "EXPIRADA");
    private ReservationPolicy() { }

    public static List<String> transitions(Reservation reservation, LocalDateTime now) {
        String state = shouldExpire(reservation, now) ? "EXPIRADA" : reservation.getStatus();
        if (state == null) return List.of();
        return switch (state) {
            case "PENDIENTE" -> isFuture(reservation, now)
                    ? List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA") : List.of("PENDIENTE");
            case "CONFIRMADA" -> isFuture(reservation, now)
                    ? List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA")
                    : hasStarted(reservation, now) ? List.of("CONFIRMADA", "COMPLETADA", "CANCELADA") : List.of("CONFIRMADA");
            case "CANCELADA" -> isFuture(reservation, now) ? List.of("CANCELADA", "PENDIENTE", "CONFIRMADA") : List.of("CANCELADA");
            case "COMPLETADA" -> List.of("COMPLETADA");
            case "EXPIRADA" -> List.of("EXPIRADA");
            default -> List.of();
        };
    }

    public static LocalTime parseTime(String value) {
        if (value == null) return null;
        try { return LocalTime.parse(value); }
        catch (DateTimeParseException ignored) {
            try { return LocalTime.parse(value.toUpperCase(Locale.ROOT), DateTimeFormatter.ofPattern("h:mm a", Locale.US)); }
            catch (DateTimeParseException invalid) { return null; }
        }
    }

    public static boolean shouldExpire(Reservation reservation, LocalDateTime now) {
        return "PENDIENTE".equals(reservation.getStatus()) && hasStarted(reservation, now);
    }

    public static boolean hasStarted(Reservation reservation, LocalDateTime now) {
        LocalTime time = parseTime(reservation.getReservationTime());
        return reservation.getReservationDate() != null && time != null
                && !reservation.getReservationDate().atTime(time).isAfter(now);
    }

    public static boolean isFuture(Reservation reservation, LocalDateTime now) {
        LocalTime time = parseTime(reservation.getReservationTime());
        return reservation.getReservationDate() != null && time != null
                && reservation.getReservationDate().atTime(time).isAfter(now);
    }

    public static boolean canCancel(Reservation reservation, LocalDateTime now) {
        return ("PENDIENTE".equals(reservation.getStatus()) || "CONFIRMADA".equals(reservation.getStatus()))
                && isFuture(reservation, now);
    }
}
