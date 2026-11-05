package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-016 DP-07: what a decision ends as; AUTHORIZED and REJECTED belong to a credit-balance refund request.
public enum DecisionOutcome {
    REFUND_FULL, CREDIT_FULL, AUTHORIZED, REJECTED
}
