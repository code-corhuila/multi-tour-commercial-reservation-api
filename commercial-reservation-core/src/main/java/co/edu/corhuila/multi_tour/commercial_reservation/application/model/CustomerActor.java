package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// Resolved by the authentication adapter; a command cannot override its tenant.
public record CustomerActor(UUID tenantId, UUID actorId, Role role) {
    public enum Role { TENANT_ADMINISTRATOR, OPERATIONAL_COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER }
}
