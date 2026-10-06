package com.spa.sistema_spa;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationLifecycle {
    private final ReservationRepository reservations;
    private final Clock clock;

    public ReservationLifecycle(ReservationRepository reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ReservationPolicy.ZONE));
    }

    /** Only call for a row already locked by the surrounding write transaction. */
    public void reconcileLocked(Reservation reservation) {
        if (ReservationPolicy.shouldExpire(reservation, now())) {
            reservation.setStatus("EXPIRADA");
            reservations.save(reservation);
        }
    }

    @Transactional
    public int reconcile() {
        var now = now();
        int changed = 0;
        for (Reservation reservation : reservations.findByStatusAndReservationDateLessThanEqualOrderByIdAsc("PENDIENTE", now.toLocalDate())) {
            if (ReservationPolicy.shouldExpire(reservation, now)) {
                // Compare-and-set: never overwrite a concurrent confirmation/cancellation.
                changed += reservations.expirePending(reservation.getId(), reservation.getReservationDate(), reservation.getReservationTime());
            }
        }
        return changed;
    }
}
