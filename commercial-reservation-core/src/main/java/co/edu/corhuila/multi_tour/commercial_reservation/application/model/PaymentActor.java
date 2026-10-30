package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.Set;
import java.util.UUID;

// Resolved by the authentication adapter from the token; a command cannot override its tenant. permissions is the token
// claim "permissions" (authentication.md); null reads as none. Shared by the payment parameters and the proof review.
public record PaymentActor(UUID tenantId, UUID actorId, Role role, Set<String> permissions) {
    public enum Role { TENANT_ADMINISTRATOR, OPERATIONAL_COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER }

    public PaymentActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public PaymentActor(UUID tenantId, UUID actorId, Role role) {
        this(tenantId, actorId, role, Set.of());
    }
}
