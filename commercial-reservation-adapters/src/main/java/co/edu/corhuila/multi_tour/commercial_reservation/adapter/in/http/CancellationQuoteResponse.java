package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationQuote;
import java.util.UUID;

// CancellationQuote of the contract (AC-13.3): what cancelling now would give; refusalReason is present only when cancellable is false.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancellationQuoteResponse(UUID reservationId, boolean cancellable, String refusalReason, long validatedValueCents,
        int penaltyBasisPoints, long penaltyCents, long refundableCents) {

    static CancellationQuoteResponse of(CancellationQuote quote) {
        var s = quote.settlement();
        return new CancellationQuoteResponse(quote.reservationId(), quote.cancellable(), quote.refusal() == null ? null : quote.refusal().name(),
                s.validated().amount(), s.penaltyBasisPoints(), s.penalty().amount(), s.refundable().amount());
    }
}
