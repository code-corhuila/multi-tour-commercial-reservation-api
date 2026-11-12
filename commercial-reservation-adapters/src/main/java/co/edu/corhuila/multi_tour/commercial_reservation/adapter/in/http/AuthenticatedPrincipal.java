package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.Set;
import java.util.UUID;

// permissions is the token claim "permissions" (authentication.md section 2); absent reads as none.
// restricted is the claim access=restricted that an inactive tenant's administrator gets (ADR-004 D5).
// exceptional is the access=exceptional or emergency of the Platform Administrator (HU-SEC-007), null for any other token.
public record AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles, Set<String> permissions,
        boolean restricted, ExceptionalAccess exceptional) {
    public AuthenticatedPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles, Set<String> permissions, boolean restricted) {
        this(sub, tenantId, roles, permissions, restricted, null);
    }

    public AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles, Set<String> permissions) {
        this(sub, tenantId, roles, permissions, false, null);
    }

    public AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles) {
        this(sub, tenantId, roles, Set.of(), false, null);
    }
}
