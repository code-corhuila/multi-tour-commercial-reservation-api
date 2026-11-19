package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// Spec 024: what the consumer of the personal emergency tells its adapter. UNKNOWN_RESERVATION leaves the event unmarked so a corrected redelivery can still apply.
public enum EmergencyOutcome {
    REFUND_DETERMINED, NO_REFUND, DUPLICATE, ALREADY_DETERMINED, IGNORED_CANCELLED, UNKNOWN_RESERVATION
}
