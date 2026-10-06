package com.spa.sistema_spa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spa.reservations.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpirationTask {
    private final ReservationLifecycle lifecycle;

    public ReservationExpirationTask(ReservationLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void reconcile() {
        lifecycle.reconcile();
    }
}
