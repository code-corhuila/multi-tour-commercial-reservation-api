package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;

// One applied change (contract ReservationModificationRecord). The status and the payment status are those of the reservation when it is read:
// the history does not keep them. The reason is free text typed by staff and is returned only to the roles that read the history.
public record ModificationRecordResponse(UUID modificationId, UUID reservationId, String changeType, int people, UUID serviceId, UUID salidaId,
        long originalFinalValueCents, long newFinalValueCents, long resultingPendingBalanceCents, long excessCents, int seatDelta,
        boolean excessDecisionOpened, String reservationStatus, String paymentStatus, String reason, UUID modifiedBy, String modifiedAt) {

    static ModificationRecordResponse of(ReservationModificationRecord r, Reservation reservation) {
        var o = r.outcome();
        return new ModificationRecordResponse(r.modificationId(), r.reservationId(), r.changeType().name(), r.newPartySize(), r.serviceId(),
                r.newSalidaId(), o.oldFinalValue().amount(), o.newFinalValue().amount(), o.newPendingBalance().amount(), o.excess().amount(),
                o.seatDelta(), o.excess().amount() > 0, reservation.reservationStatus().name(), reservation.paymentStatus().name(), r.reason(),
                r.registeredBy(), r.registeredAt().toString());
    }
}
