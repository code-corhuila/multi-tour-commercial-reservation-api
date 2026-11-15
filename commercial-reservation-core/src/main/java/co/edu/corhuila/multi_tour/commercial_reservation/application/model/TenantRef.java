package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// What the directory knows about a public identifier: the tenant and whether its offer is served (DP-02).
public record TenantRef(UUID tenantId, boolean active) {
}
