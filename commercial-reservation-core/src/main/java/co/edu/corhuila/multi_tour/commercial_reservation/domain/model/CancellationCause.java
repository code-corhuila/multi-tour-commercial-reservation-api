package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The three origins of a cancellation (the codes of chk_reservation_cancellation_cause). HU-COM-011 uses the first; HU-COM-013 the others.
public enum CancellationCause {
    PAYMENT_EXPIRY, CUSTOMER_REQUEST, NOT_ATTRIBUTABLE_TO_CUSTOMER
}
