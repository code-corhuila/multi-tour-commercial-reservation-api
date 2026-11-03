package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// The tenant events of identity-audit this service consumes. Localization is accepted although it is not emitted yet (DP-TSC-02).
public enum TenantEventType {
    TENANT_CREATED, TENANT_INACTIVATED, TENANT_REACTIVATED, TENANT_LOCALIZATION_CHANGED
}
