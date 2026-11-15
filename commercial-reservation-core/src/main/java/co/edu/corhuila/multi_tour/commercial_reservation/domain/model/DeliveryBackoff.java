package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;

// ADR-004 D10: the wait before the next delivery of an outbox event starts at 5 seconds, doubles with every failed attempt and stops at 15 minutes;
// after the twentieth attempt the event is marked failed and stays visible (HU-COM-015 AC-15.6, AC-15.7).
public final class DeliveryBackoff {
    public static final int MAX_ATTEMPTS = 20;
    private static final Duration FIRST = Duration.ofSeconds(5);
    private static final Duration CEILING = Duration.ofMinutes(15);

    private DeliveryBackoff() {
    }

    // The wait after the given number of failed attempts (1 for the first).
    public static Duration waitAfter(int attempts) {
        if (attempts < 1) throw new IllegalArgumentException("attempts starts at 1");
        long seconds = FIRST.getSeconds() << Math.min(attempts - 1, 20);
        return seconds >= CEILING.getSeconds() ? CEILING : Duration.ofSeconds(seconds);
    }
}
