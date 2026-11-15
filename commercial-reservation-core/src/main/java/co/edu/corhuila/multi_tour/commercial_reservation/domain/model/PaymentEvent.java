package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// An event of the payment family, written to the outbox in the transaction of the change (domain-events.md).
public interface PaymentEvent {
    UUID tenantId();
    UUID reservationId();
    Instant occurredAt();
}
