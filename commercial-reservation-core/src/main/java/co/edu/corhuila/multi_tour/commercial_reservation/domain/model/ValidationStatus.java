package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// State of a transfer proof; cash has none and is never a proof to review (DP-01). Codes of the db (X-1).
public enum ValidationStatus {
    IN_VALIDATION, APPROVED, REJECTED
}
