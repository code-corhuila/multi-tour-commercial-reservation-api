package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import java.util.UUID;

// AC-12.2: what the change would give, or the reason the terms refuse it (then there is no outcome). Nothing is written to answer it. The
// projected value is the one of the new party and the discount is what the fixed lines take from it (projected - discount = new final value);
// a refused quote answers both as the reservation has them.
public record ModificationQuote(UUID reservationId, boolean allowed, Reason reason, ModificationOutcome outcome, boolean seatsAvailable,
        Cents projectedValue, Cents discount) {

    public enum Reason { NO_POLICY, NOT_ALLOWED, TOO_LATE, NO_AVAILABILITY }
}
