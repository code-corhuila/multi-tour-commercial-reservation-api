package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// The reservation is found by the two reads that already decide who may see it (tenant, and own scope for a Final Customer),
// so a foreign or missing one fails the same way before the additional discount is looked up.
public class GetReservationDiscountBreakdownUseCase implements GetReservationDiscountBreakdown {

    private final GetReservation reservations;
    private final GetOwnReservation ownReservations;
    private final AdditionalDiscountRepository discounts;

    public GetReservationDiscountBreakdownUseCase(GetReservation reservations, GetOwnReservation ownReservations,
            AdditionalDiscountRepository discounts) {
        this.reservations = reservations;
        this.ownReservations = ownReservations;
        this.discounts = discounts;
    }

    @Override
    public View get(UUID tenantId, UUID reservationId) {
        return view(reservations.get(tenantId, reservationId));
    }

    @Override
    public View getOwn(UUID tenantId, UUID accountId, UUID reservationId) {
        return view(ownReservations.get(tenantId, accountId, reservationId));
    }

    private View view(Reservation reservation) {
        var additional = discounts.findByReservation(reservation.tenantId(), reservation.reservationId());
        return new View(reservation, additional, excess(reservation, additional));
    }

    // Validated money is the final value fixed at creation minus the pending balance; the new value is the additional line's subtotal.
    private static Cents excess(Reservation reservation, Optional<AdditionalDiscount> additional) {
        return additional.map(found -> {
            long paid = reservation.finalValue().amount() - reservation.pendingBalance().amount();
            long newValue = found.line().subtotal().amount();
            return new Cents(Math.max(0, paid - newValue));
        }).orElse(new Cents(0));
    }
}
