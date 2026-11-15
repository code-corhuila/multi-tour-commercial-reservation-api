package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// The tenant and the actor come from the token, never from the body (AC-09.9). The amount is the value declared by the
// payer and is stored as declared; the amount to pay is always the service's (AC-09.15). The proof is the raw content.
public record RegisterTransferCommand(UUID tenantId, UUID actorId, UUID reservationId, Long amountCents, byte[] proof) {
}
