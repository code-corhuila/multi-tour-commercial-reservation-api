package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// The future authentication adapter supplies this resolved context; commands cannot override its tenant.
public record EstablishmentActor(UUID tenantId, UUID actorId, Role role) {
    public enum Role { TENANT_ADMINISTRATOR, OPERATIONAL_COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST }
}
