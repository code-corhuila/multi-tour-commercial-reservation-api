package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// The delivery data of the outbox for job 7 (ADR-004 D4, HU-COM-015 plan D-08). Every write touches the delivery columns of one event and nothing else, and
// answers false when the event is no longer pending (published, failed, or changed by another caller), so a repeat changes nothing.
public interface OutboxDeliveryStore {

    // The events never tried or whose wait has elapsed at now, never-tried first and then the oldest.
    OutboxEventPage pending(Instant now, int page, int limit);

    Optional<OutboxEventView> find(UUID eventId);

    boolean markPublished(UUID eventId, Instant now);

    // expectedAttempts guards a concurrent caller; failedAt marks the event that exhausted its attempts, and then nextAttemptAt is null.
    boolean recordAttempt(UUID eventId, int expectedAttempts, String lastError, Instant nextAttemptAt, Instant failedAt);
}
