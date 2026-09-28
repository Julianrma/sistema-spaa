package com.spa.sistema_spa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final ConcurrentHashMap<String, AttemptWindow> attemptsByAddress = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String address) {
        AttemptWindow window = attemptsByAddress.get(address);
        if (window == null) {
            return false;
        }
        synchronized (window) {
            if (expired(window)) {
                attemptsByAddress.remove(address, window);
                return false;
            }
            return window.failures >= MAX_ATTEMPTS;
        }
    }

    public void recordFailure(String address) {
        Instant now = clock.instant();
        attemptsByAddress.compute(address, (key, current) -> {
            AttemptWindow window = current;
            if (window == null || !now.isBefore(window.startedAt.plus(WINDOW))) {
                window = new AttemptWindow(now);
            }
            synchronized (window) {
                window.failures++;
            }
            return window;
        });
        evictExpiredEntries();
    }

    public void reset(String address) {
        attemptsByAddress.remove(address);
    }

    private boolean expired(AttemptWindow window) {
        return !clock.instant().isBefore(window.startedAt.plus(WINDOW));
    }

    private void evictExpiredEntries() {
        if (attemptsByAddress.size() <= MAX_TRACKED_ADDRESSES) {
            return;
        }
        attemptsByAddress.entrySet().removeIf(entry -> expired(entry.getValue()));
        if (attemptsByAddress.size() > MAX_TRACKED_ADDRESSES) {
            attemptsByAddress.keySet().stream().limit(attemptsByAddress.size() - MAX_TRACKED_ADDRESSES)
                    .forEach(attemptsByAddress::remove);
        }
    }

    private static final class AttemptWindow {
        private final Instant startedAt;
        private int failures;

        private AttemptWindow(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}