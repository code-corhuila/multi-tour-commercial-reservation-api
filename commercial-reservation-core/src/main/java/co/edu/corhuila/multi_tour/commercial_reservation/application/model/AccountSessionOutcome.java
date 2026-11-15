package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// APPLIED: the cutoff was kept or moved forward. UNCHANGED: the instant is not later than the cutoff held. ALREADY_PROCESSED: a repeat of the event id.
public enum AccountSessionOutcome {
    APPLIED, UNCHANGED, ALREADY_PROCESSED
}
