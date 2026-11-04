package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationSettlement;
import java.time.Instant;
import java.util.UUID;

// HU-COM-013 AC-13.10, RN-10: the record of a cancellation, written once and never edited. The reason is stored here and never evented
// or logged (RNF-03).
public record CancellationRecord(UUID reservationId, String reason, CancellationCause origin, CancellationSettlement settlement,
        boolean refundDecisionOpened, UUID cancelledBy, Instant cancelledAt) {
}
