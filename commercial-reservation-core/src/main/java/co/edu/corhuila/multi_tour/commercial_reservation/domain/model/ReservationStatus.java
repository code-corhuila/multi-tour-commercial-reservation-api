package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The codes of the db (chk_reservation_status). Each story adds the transitions it owns: HU-COM-009 and HU-COM-010 confirm.
public enum ReservationStatus {
    PENDING_PAYMENT, CONFIRMED, IN_EXECUTION, COMPLETED, CANCELLED
}
