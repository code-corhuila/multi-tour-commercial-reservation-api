package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

// The current breakdown of a reservation (HU-COM-004, AC-04.3, AC-04.12): the lines fixed at creation and, when it exists, the
// additional discount. A read: it recalculates nothing and writes nothing.
public interface GetReservationDiscountBreakdown {

    View get(UUID tenantId, UUID reservationId);

    // A Final Customer: only the reservation of the customer of their account; any other one looks missing.
    View getOwn(UUID tenantId, UUID accountId, UUID reservationId);

    // The excess is what was validated above the value after the additional discount; zero without one.
    record View(Reservation reservation, Optional<AdditionalDiscount> additional, Cents excess) {
    }
}
