package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// The quote of a modification (contract ModificationQuote). A refused quote has no recomputation: it answers the values as they are.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModificationQuoteResponse(UUID reservationId, String changeType, boolean allowed, String refusalReason, long originalFinalValueCents,
        long newFinalValueCents, long differenceCents, long validatedValueCents, long newPendingBalanceCents, long excessCents, int seatDelta,
        boolean seatsAvailable) {

    static ModificationQuoteResponse of(ModificationQuote quote, ChangeType type, Reservation reservation) {
        long validated = reservation.finalValue().amount() - reservation.pendingBalance().amount();
        var outcome = quote.outcome();
        long original = outcome == null ? reservation.finalValue().amount() : outcome.oldFinalValue().amount();
        long recomputed = outcome == null ? original : outcome.newFinalValue().amount();
        return new ModificationQuoteResponse(quote.reservationId(), type.name(), quote.allowed(), quote.reason() == null ? null : quote.reason().name(),
                original, recomputed, recomputed - original, validated,
                outcome == null ? reservation.pendingBalance().amount() : outcome.newPendingBalance().amount(),
                outcome == null ? 0 : outcome.excess().amount(), outcome == null ? 0 : outcome.seatDelta(), quote.seatsAvailable());
    }
}
