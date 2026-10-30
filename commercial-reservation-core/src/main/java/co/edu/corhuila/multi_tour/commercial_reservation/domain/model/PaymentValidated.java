package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Emitted by recordValidatedPayment: for cash here and for an approved transfer in HU-COM-010. The amount is the applied
// one, never the declared one.
public record PaymentValidated(UUID tenantId, UUID reservationId, Cents amount, PaymentStatus paymentStatus,
        Cents pendingBalance, UUID validatorId, Instant occurredAt) implements PaymentEvent {
}
