package com.spa.sistema_spa;

import java.math.BigDecimal;
import java.util.List;

public record ReservationSummary(Reservation reservation, String reference, String serviceName,
                                 String branchName, String masseuseName, Integer duration,
                                 BigDecimal price, boolean cancellable, List<String> transitions) {
    public static ReservationSummary from(Reservation r, SpaService service, Branch branch, Masseuse masseuse,
                                          java.time.LocalDateTime now) {
        return new ReservationSummary(r, "SMB-" + r.getId(),
                service == null ? "Servicio no disponible" : service.getName(),
                branch == null ? "Sucursal no disponible" : branch.getName(),
                masseuse == null ? "Sin profesional asignada" : masseuse.getName(),
                service == null ? null : service.getDurationMinutes(), service == null ? null : service.getPrice(),
                ReservationPolicy.canCancel(r, now), ReservationPolicy.transitions(r, now).stream()
                    .filter(state -> !state.equals(r.getStatus())).toList());
    }

    public boolean resolutionRequired() {
        return "CONFIRMADA".equals(reservation.getStatus()) && !cancellable
                && transitions.contains("COMPLETADA");
    }
}
