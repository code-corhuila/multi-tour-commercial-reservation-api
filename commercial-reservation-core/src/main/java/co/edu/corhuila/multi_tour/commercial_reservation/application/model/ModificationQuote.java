package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import java.util.UUID;

// AC-12.2: what the change would give, or the reason the terms refuse it (then there is no outcome). Nothing is written to answer it.
public record ModificationQuote(UUID reservationId, boolean allowed, Reason reason, ModificationOutcome outcome) {

    public enum Reason { NO_POLICY, NOT_ALLOWED, TOO_LATE }
}
