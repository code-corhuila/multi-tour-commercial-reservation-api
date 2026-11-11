package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.Set;
import java.util.UUID;

// The claims of the token of an exceptional or emergency access (HU-SEC-007, ADR-004 D5): access is "exceptional" or "emergency", scope the authorized areas and authorizationId the id of the access.
public record ExceptionalAccess(String access, Set<String> scope, UUID authorizationId) {
    public ExceptionalAccess {
        scope = Set.copyOf(scope);
    }
}
