package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.List;

// One page of the payment history of a reservation, newest first, with the count of all the attempts.
public record ReservationPayments(Reservation reservation, List<PaymentAttempt> attempts, int total) {
}
