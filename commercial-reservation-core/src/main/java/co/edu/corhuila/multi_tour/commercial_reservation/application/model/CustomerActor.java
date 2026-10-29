package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.Set;
import java.util.UUID;

// Resolved by the authentication adapter; a command cannot override its tenant.
// permissions is the token claim "permissions" (authentication.md section 2); null reads as none.
public record CustomerActor(UUID tenantId, UUID actorId, Role role, Set<String> permissions) {
    public enum Role { TENANT_ADMINISTRATOR, OPERATIONAL_COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER }

    public CustomerActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public CustomerActor(UUID tenantId, UUID actorId, Role role) {
        this(tenantId, actorId, role, Set.of());
    }
}
