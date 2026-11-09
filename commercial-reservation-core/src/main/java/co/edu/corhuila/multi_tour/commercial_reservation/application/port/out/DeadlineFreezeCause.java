package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

// HU-COM-020 DP-D20-3, AC-20.4: why a deadline is frozen. Only the deadlines frozen by the inactivation of the tenant are resumed by its reactivation.
public enum DeadlineFreezeCause {
    TENANT_INACTIVE, DEPARTURE_CANCELLED_OR_INTERRUPTED
}
