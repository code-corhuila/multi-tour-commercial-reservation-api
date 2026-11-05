package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-016 DP-07, DP-05: who authorized the refund; a refund Multitour determines counts as authorized.
public enum AuthorizationMode {
    TENANT_ADMINISTRATOR, AUTOMATIC_DETERMINATION
}
