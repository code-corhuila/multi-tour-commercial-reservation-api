package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// The attempt and the reservation as they stand after it, for the answer of the registration.
public record RegisteredPayment(Reservation reservation, PaymentAttempt attempt) {
}
