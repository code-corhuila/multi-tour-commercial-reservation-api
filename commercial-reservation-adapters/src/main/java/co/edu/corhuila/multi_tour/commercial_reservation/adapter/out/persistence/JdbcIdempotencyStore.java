package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import java.util.Optional;
import java.util.UUID;

// The core port over the key table of the adapter, so the tests that replace IdempotencyKeys replace it for the core too.
public final class JdbcIdempotencyStore implements IdempotencyStore {
    private final IdempotencyKeys keys;

    public JdbcIdempotencyStore(IdempotencyKeys keys) {
        this.keys = keys;
    }

    @Override
    public Optional<UUID> find(UUID tenantId, String key) {
        return keys.findResourceId(tenantId, key);
    }

    @Override
    public boolean register(UUID tenantId, String key, String resourceType, UUID resourceId) {
        return keys.tryRegister(tenantId, key, resourceType, resourceId);
    }
}
