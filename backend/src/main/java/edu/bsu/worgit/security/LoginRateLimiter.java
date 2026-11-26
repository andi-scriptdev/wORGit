package edu.bsu.worgit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import edu.bsu.worgit.exception.TooManyAttemptsException;

/**
 * Blocks a (user id, client IP) pair after too many failed logins within a window. In memory, so it
 * resets on restart and is per server instance (fine for one instance; ASSUMED).
 */
@Component
public class LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties props, Clock clock) {
        this.maxFailures = props.login().maxFailures();
        this.window = props.login().window();
        this.clock = clock;
    }

    /** Throws TooManyAttemptsException when the pair is currently blocked. Does not record anything. */
    public void checkAllowed(String userId, String ip) {
        Deque<Instant> attempts = failures.get(key(userId, ip));
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= maxFailures) {
                throw new TooManyAttemptsException();
            }
        }
    }

    public void recordFailure(String userId, String ip) {
        if (failures.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key(userId, ip), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void reset(String userId, String ip) {
        failures.remove(key(userId, ip));
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private void cleanup() {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String key(String userId, String ip) {
        return (userId == null ? "" : userId.toLowerCase(Locale.ROOT)) + "|" + ip;
    }
}
