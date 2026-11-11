package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import java.time.Instant;

// The open deadline of a reservation: which one applies and when it ends. HU-COM-011 owns its extension and its expiry.
public record PaymentDeadline(DeadlineKind kind, Instant dueAt) {
}
