package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// What the reader of the list returns: the attempt with the reservation that holds it.
public record ProofSource(Reservation reservation, PaymentAttempt attempt) {
}
