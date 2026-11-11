package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// AC-11.6: the stored deadline with the departure start and the effective deadline derived from both.
public record DeadlineView(UUID reservationId, PaymentDeadline deadline, Instant departureStartsAt, Instant effective) {
}
