package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// Cash received by the office. The actor is the user of the token, forwarded to Cash as the one who registered it (DP-02).
public record RegisterCashCommand(UUID tenantId, UUID actorId, UUID reservationId, Long amountCents) {
}
