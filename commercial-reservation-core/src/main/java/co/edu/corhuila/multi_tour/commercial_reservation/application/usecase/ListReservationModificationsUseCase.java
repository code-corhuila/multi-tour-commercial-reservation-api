package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import java.util.UUID;

// The role is checked before any load; a reservation of another tenant looks missing (AC-12.10).
public class ListReservationModificationsUseCase implements ListReservationModifications {

    private final ReservationRepository reservations;
    private final ReservationModifications history;

    public ListReservationModificationsUseCase(ReservationRepository reservations, ReservationModifications history) {
        this.reservations = reservations;
        this.history = history;
    }

    @Override
    public ModificationPage list(PaymentActor actor, UUID reservationId, int page, int limit) {
        CancellationAuthorization.requireRead(actor);
        reservations.findByTenantAndId(actor.tenantId(), reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return history.page(actor.tenantId(), reservationId, page, limit);
    }
}
