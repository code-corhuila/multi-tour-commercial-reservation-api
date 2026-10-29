package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// What the consumer adapter logs by event id and nothing else (AC-06.16). Only IGNORED_UNKNOWN leaves the event
// unmarked, so a redelivery after the scheduled event can still apply it (DP-16).
public enum MirrorOutcome {
    APPLIED, DUPLICATE, IGNORED_OUT_OF_ORDER, IGNORED_UNKNOWN
}
