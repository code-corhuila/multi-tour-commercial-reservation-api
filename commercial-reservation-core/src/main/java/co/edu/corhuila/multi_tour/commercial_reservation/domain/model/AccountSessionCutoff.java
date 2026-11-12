package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// From when the sessions of an account no longer count (ADR-004 D5): a token issued before revokedFrom is refused. tenantId is null for the
// account of a Platform Administrator, which belongs to no tenant.
public record AccountSessionCutoff(UUID accountId, UUID tenantId, Instant revokedFrom) {
    public AccountSessionCutoff {
        if (accountId == null) throw new DomainException("accountId", "accountId is required");
        if (revokedFrom == null) throw new DomainException("revokedFrom", "revokedFrom is required");
    }

    // Whether a token issued at the given instant (null when the token has none) no longer counts. Compared in whole seconds, the precision of
    // the token: a sign-in made in the second of the revocation is a new session. A token with no iat cannot be shown to be later.
    public boolean refuses(Instant issuedAt) {
        return issuedAt == null || issuedAt.isBefore(revokedFrom.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }
}
