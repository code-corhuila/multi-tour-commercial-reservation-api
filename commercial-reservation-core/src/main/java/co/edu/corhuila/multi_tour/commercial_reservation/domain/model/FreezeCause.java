package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-020 DP-D20-3, AC-20.4: why a deadline is frozen. Only the deadlines frozen by the inactivation of the tenant are resumed by its reactivation; one frozen by the cancellation or interruption
// of its departure stays frozen and belongs to the rescheduling (HU-COM-018).
public enum FreezeCause {
    TENANT_INACTIVE, DEPARTURE_CANCELLED_OR_INTERRUPTED
}
