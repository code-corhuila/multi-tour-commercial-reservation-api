package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// Cash received by the office. The actor is the user of the token, forwarded to Cash as the one who registered it (DP-02).
// idempotencyKey is the Idempotency-Key of the request (null when the caller has none); it makes the income of the cash stable.
public record RegisterCashCommand(UUID tenantId, UUID actorId, UUID reservationId, Long amountCents, String idempotencyKey) {

    public RegisterCashCommand(UUID tenantId, UUID actorId, UUID reservationId, Long amountCents) {
        this(tenantId, actorId, reservationId, amountCents, null);
    }

    public RegisterCashCommand withKey(String key) {
        return new RegisterCashCommand(tenantId, actorId, reservationId, amountCents, key);
    }
}
