package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// One row of the reservation list (portal screen 10): the lead customer and the tour are shown by name, so the query joins them.
// HU-COM-014 AC-14.1: the row also carries the payment type and method, the validated value as stored (final value minus the pending
// balance, never recomputed here) and the state of the latest attempt, which is absent when there is none or the payment was cash.
public record ReservationListItem(UUID reservationId, String customerName, String tourName, Instant departureStartsAt,
        ReservationStatus reservationStatus, PaymentStatus paymentStatus, Cents pendingBalance, PaymentType paymentType,
        PaymentMethod paymentMethod, Cents validatedValue, Optional<ValidationStatus> latestProof) {

    // The row of the earlier stories, which did not read the payment: no type or method known, nothing validated, no proof.
    public ReservationListItem(UUID reservationId, String customerName, String tourName, Instant departureStartsAt,
            ReservationStatus reservationStatus, PaymentStatus paymentStatus, Cents pendingBalance) {
        this(reservationId, customerName, tourName, departureStartsAt, reservationStatus, paymentStatus, pendingBalance, null, null,
                new Cents(0), Optional.empty());
    }
}
