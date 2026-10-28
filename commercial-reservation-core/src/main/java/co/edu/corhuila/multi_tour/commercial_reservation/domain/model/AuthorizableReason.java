package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.UUID;

// One entry of the tenant catalogue of authorizable reasons (DP-06). It is disabled, never deleted.
public record AuthorizableReason(UUID tenantId, UUID reasonId, String text, boolean enabled) {

    public static AuthorizableReason create(UUID tenantId, UUID reasonId, String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException("reason", "reason is required");
        }
        return new AuthorizableReason(tenantId, reasonId, text.trim(), true);
    }

    public AuthorizableReason disabled() {
        return new AuthorizableReason(tenantId, reasonId, text, false);
    }
}
