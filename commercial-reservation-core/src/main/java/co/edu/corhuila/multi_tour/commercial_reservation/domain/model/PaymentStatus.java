package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The financial situation, derived from validated values only (RN-RES-006A, models.md). English codes of the db (X-2).
public enum PaymentStatus {
    NO_PAYMENT, PARTIAL, DEPOSIT_RECEIVED, BALANCE_PENDING, FULLY_PAID
}
