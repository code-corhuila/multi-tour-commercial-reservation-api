package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Emitted by recordValidatedPayment: for cash here and for an approved transfer in HU-COM-010. The amount is the applied
// one, never the declared one. attemptId is the attempt (proof or cash receipt) it comes from; it is null for a credit balance applied,
// which has no attempt, and then the key is left out of the event.
public record PaymentValidated(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount, PaymentStatus paymentStatus,
        Cents pendingBalance, UUID validatorId, Instant occurredAt) implements PaymentEvent {
}
