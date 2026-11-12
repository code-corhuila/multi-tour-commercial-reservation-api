package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-CSH-003 DP-RM-B3: commercialValue is the final value of the reservation at the instant of the confirmation (the cash report adds it to the
// confirmed sales of the month); the stored payload names it commercialValueCents.
public record ReservationConfirmed(UUID tenantId, UUID reservationId, PaymentStatus paymentStatus, int allocatedCapacity,
        Cents commercialValue, Instant occurredAt) implements PaymentEvent {
}
