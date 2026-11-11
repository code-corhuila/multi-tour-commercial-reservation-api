package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import java.util.UUID;

// ReservationCancellation of the contract (AC-13.10): the record whole. The reason is shown here and nowhere else (RNF-03).
public record CancellationResponse(UUID reservationId, String reservationStatus, String reason, String origin, long validatedValueCents,
        int penaltyBasisPoints, long penaltyCents, long refundableCents, boolean refundDecisionOpened, UUID cancelledBy, String cancelledAt) {

    static CancellationResponse of(CancellationRecord record) {
        var s = record.settlement();
        return new CancellationResponse(record.reservationId(), "CANCELLED", record.reason(), record.origin().name(), s.validated().amount(),
                s.penaltyBasisPoints(), s.penalty().amount(), s.refundable().amount(), record.refundDecisionOpened(), record.cancelledBy(),
                record.cancelledAt().toString());
    }
}
