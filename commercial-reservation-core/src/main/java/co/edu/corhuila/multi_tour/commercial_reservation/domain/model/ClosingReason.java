package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-018 DP-13: why an episode closed. ACCEPTED goes with the state RESCHEDULED; every other reason goes with CANCELLED (cause NOT_ATTRIBUTABLE_TO_CUSTOMER).
public enum ClosingReason {
    ACCEPTED, REJECTED_BY_CUSTOMER, NO_ALTERNATIVE, PROPOSAL_PERIOD_EXPIRED, RESPONSE_DEADLINE_EXPIRED, EPISODE_LIMIT_REACHED,
    PROPOSED_DEPARTURE_CANCELLED_WHILE_INACTIVE
}
