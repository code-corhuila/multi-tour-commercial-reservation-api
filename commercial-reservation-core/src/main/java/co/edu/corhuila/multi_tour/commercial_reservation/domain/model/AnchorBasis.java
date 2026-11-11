package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-019 RN-02: where the year of a reservation starts counting. The wire codes are the ones the deletion record of the db accepts.
public enum AnchorBasis {
    SERVICE_END, ESTIMATED_END, CLOSING, CANCELLATION_NO_VALUES, CANCELLATION_BLOCKED
}
