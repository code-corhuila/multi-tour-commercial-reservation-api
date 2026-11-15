package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// The decided attempt and the reservation as it stands afterwards (read again when the cancellation of HU-COM-011 changed it).
// Applied and excess are zero for a rejection.
public record DecidedProof(Reservation reservation, PaymentAttempt attempt, Cents applied, Cents excess) {
}
