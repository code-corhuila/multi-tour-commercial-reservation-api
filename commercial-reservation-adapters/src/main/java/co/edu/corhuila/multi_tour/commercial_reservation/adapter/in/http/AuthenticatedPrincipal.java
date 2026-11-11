package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedPrincipal(String sub, UUID tenantId, Set<String> roles) {
    public AuthenticatedPrincipal {
        roles = Set.copyOf(roles);
    }
}
