package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

// One row of the reservation list (portal screen 10): the lead customer and the tour are shown by name, so the query joins them.
// The state of the proof is absent until HU-COM-010.
public record ReservationListItem(UUID reservationId, String customerName, String tourName, Instant departureStartsAt,
        ReservationStatus reservationStatus, PaymentStatus paymentStatus, Cents pendingBalance) {
}
