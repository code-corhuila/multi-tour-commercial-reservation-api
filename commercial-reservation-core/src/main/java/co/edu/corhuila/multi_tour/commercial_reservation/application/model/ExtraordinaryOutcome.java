package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// Spec 025: what the consumer tells its adapter. UNKNOWN_RESERVATION (no such reservation for the tenant, or not on the departure of the event) leaves the event unmarked.
public enum ExtraordinaryOutcome {
    EPISODE_OPENED, ALREADY_OPEN, DUPLICATE, IGNORED_FINAL, UNKNOWN_RESERVATION
}
