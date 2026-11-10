package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication.AppliedBy;
import java.time.Instant;
import java.util.UUID;

// HU-COM-017 AC-17.18: a credit balance was applied to a target reservation, which is the aggregate of the event. It carries identifiers, the amount and the
// capacity of whoever applied it, never a name or a reason.
public record CreditBalanceApplied(UUID tenantId, UUID reservationId, Instant occurredAt, UUID creditBalanceId, UUID sourceReservationId, Cents appliedAmount,
        AppliedBy appliedBy, UUID actorId) implements PaymentEvent {
}
