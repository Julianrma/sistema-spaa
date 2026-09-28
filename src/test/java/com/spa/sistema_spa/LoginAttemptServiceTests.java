package com.spa.sistema_spa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTests {

    @Test
    void blocksAfterFiveFailuresAndResetsAfterSuccess() {
        LoginAttemptService attempts = new LoginAttemptService(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        for (int failure = 0; failure < 5; failure++) {
            attempts.recordFailure("192.0.2.10");
        }

        assertTrue(attempts.isBlocked("192.0.2.10"));
        assertFalse(attempts.isBlocked("192.0.2.11"));
        attempts.reset("192.0.2.10");
        assertFalse(attempts.isBlocked("192.0.2.10"));
    }

    @Test
    void releasesAddressAfterFifteenMinuteWindow() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        LoginAttemptService attempts = new LoginAttemptService(clock);
        for (int failure = 0; failure < 5; failure++) {
            attempts.recordFailure("192.0.2.20");
        }

        now.set(Instant.EPOCH.plus(Duration.ofMinutes(15)));
        assertFalse(attempts.isBlocked("192.0.2.20"));
    }
}