package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// The fact that the sessions of an account were revoked (ADR-004 D5; domain-events.md: tenant, account, "from" time). tenantId is optional:
// the account of a Platform Administrator has none, and the cutoff is by account.
public record AccountSessionsRevoked(UUID eventId, UUID accountId, UUID tenantId, Instant revokedFrom) {
    public AccountSessionsRevoked {
        if (eventId == null) throw new DomainException("eventId", "eventId is required");
        if (accountId == null) throw new DomainException("accountId", "accountId is required");
        if (revokedFrom == null) throw new DomainException("revokedFrom", "revokedFrom is required");
    }
}
