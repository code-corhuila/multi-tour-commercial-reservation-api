package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationSettlement;
import java.util.UUID;

// HU-COM-013 AC-13.3: what cancelling now would give, with no side effect. refusal is the code the cancellation would answer with when
// cancellable is false.
public record CancellationQuote(UUID reservationId, boolean cancellable, Refusal refusal, CancellationSettlement settlement) {
    public enum Refusal { INVALID_STATUS_TRANSITION, BUSINESS_RULE_VIOLATION }
}
