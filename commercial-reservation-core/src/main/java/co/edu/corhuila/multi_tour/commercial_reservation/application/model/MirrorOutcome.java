package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// What the consumer adapter logs by event id and nothing else (AC-06.16). IGNORED_UNKNOWN and AWAITING_DEPARTURE leave the
// event unmarked, so a redelivery can still apply it. AWAITING_DEPARTURE is an event whose departure has no row yet: the
// adapter answers retry later until SalidaScheduled creates the row (DP-16, DD-8).
public enum MirrorOutcome {
    APPLIED, DUPLICATE, IGNORED_OUT_OF_ORDER, IGNORED_UNKNOWN, AWAITING_DEPARTURE
}
