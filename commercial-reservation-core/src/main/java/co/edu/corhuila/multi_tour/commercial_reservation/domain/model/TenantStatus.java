package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The two states the copy holds; PENDING and FAILED tenants never reach this service (DP-TSC-11).
public enum TenantStatus {
    ACTIVE, INACTIVE
}
