package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

// HTTP tests replace this store; database tests verify atomic registration and rollback.
public interface IdempotencyKeys {
    boolean tryRegister(UUID tenantId, String key, String resourceType, UUID resourceId);

    Optional<UUID> findResourceId(UUID tenantId, String key);
}
