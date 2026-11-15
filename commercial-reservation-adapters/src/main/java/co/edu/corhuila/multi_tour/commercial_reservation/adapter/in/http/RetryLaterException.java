package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.UUID;

// A departure event that cannot be applied yet because its departure has no row (DP-16, DD-8): the answer is 503 with a
// wait hint, so the relay delivers it again with growing waits until SalidaScheduled creates the row. It carries the
// event id for the log and never a value of the payload.
public final class RetryLaterException extends RuntimeException {
    private final UUID eventId;

    public RetryLaterException(UUID eventId) {
        super("deliver the event again later");
        this.eventId = eventId;
    }

    public UUID eventId() {
        return eventId;
    }
}
