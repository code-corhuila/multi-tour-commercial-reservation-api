package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The deadline_kind of the db: the one that applies to a payment is read through PaymentDeadlineReader (spec X-6).
public enum DeadlineKind {
    INITIAL_PAYMENT, BALANCE, PENDING_DIFFERENCE
}
