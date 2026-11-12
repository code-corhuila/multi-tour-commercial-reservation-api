package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import java.util.UUID;

// The role is checked before any load; a change of another tenant or of another reservation looks missing.
public class GetReservationModificationUseCase implements GetReservationModification {

    private final ReservationModifications history;

    public GetReservationModificationUseCase(ReservationModifications history) {
        this.history = history;
    }

    @Override
    public ReservationModificationRecord get(PaymentActor actor, UUID reservationId, UUID modificationId) {
        CancellationAuthorization.requireRead(actor);
        return history.find(actor.tenantId(), modificationId).filter(row -> row.reservationId().equals(reservationId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
