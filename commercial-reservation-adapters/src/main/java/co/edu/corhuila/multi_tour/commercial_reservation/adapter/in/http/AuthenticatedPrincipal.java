package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.Set;
import java.util.UUID;

// permissions is the token claim "permissions" (authentication.md section 2); absent reads as none.
public record AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles, Set<String> permissions) {
    public AuthenticatedPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles) {
        this(sub, tenantId, roles, Set.of());
    }
}
