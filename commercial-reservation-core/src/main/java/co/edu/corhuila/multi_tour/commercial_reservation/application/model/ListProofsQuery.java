package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;

// The status is optional and defaults to the proofs in validation; overdue keeps only those with the 24 hour mark.
public record ListProofsQuery(PaymentActor actor, ValidationStatus status, boolean overdue, int page, int limit) {
}
