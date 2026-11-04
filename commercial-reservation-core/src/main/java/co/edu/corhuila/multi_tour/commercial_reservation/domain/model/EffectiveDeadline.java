package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Duration;
import java.time.Instant;

// RN-02 (RN-RES-006, RN-EJE-001): the effective deadline is the lower of the due instant plus the extension and the departure start.
// Derived at read time, never stored. A null start means the departure is unknown and caps nothing.
public final class EffectiveDeadline {
    private EffectiveDeadline() {}

    public static Instant of(Instant dueAt, int extensionHours, Instant departureStart) {
        Instant extended = dueAt.plus(Duration.ofHours(extensionHours));
        return departureStart != null && departureStart.isBefore(extended) ? departureStart : extended;
    }
}
