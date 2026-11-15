package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-016 DP-07: the four decision kinds plus the two causes that need no decision.
public enum RefundOrigin {
    PENDING_TREATMENT, MODIFICATION_EXCESS, CUSTOMER_CANCELLATION, CREDIT_BALANCE_REFUND_REQUEST,
    NOT_ATTRIBUTABLE_TO_CUSTOMER, PERSONAL_EMERGENCY_DURING_EXECUTION
}
