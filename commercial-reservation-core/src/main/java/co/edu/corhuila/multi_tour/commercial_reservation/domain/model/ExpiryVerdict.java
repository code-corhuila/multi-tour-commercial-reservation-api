package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Why a reservation may or may not be cancelled for non-payment at a given instant (RN-04, RN-08, RN-09).
public enum ExpiryVerdict {
    EXPIRABLE, NOT_DUE, PAID, PROOF_IN_VALIDATION, FROZEN, ALREADY_CANCELLED
}
