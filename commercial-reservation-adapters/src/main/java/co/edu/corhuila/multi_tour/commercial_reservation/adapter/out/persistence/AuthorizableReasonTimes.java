package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// What the reason entity does not carry but the contract shows (createdAt, updatedAt), and the lock that keeps
// two concurrent disables from emptying the catalogue of a tenant.
public interface AuthorizableReasonTimes {
    record Times(Instant createdAt, Instant updatedAt) {}

    Map<UUID, Times> timesByTenant(UUID tenantId);

    // Must run inside a transaction: the row locks last until it ends.
    void lockCatalogue(UUID tenantId);
}
