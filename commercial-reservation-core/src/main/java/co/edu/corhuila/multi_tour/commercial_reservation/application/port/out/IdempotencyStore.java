package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.Optional;
import java.util.UUID;

// The keys of the requests already answered, per tenant (norm 5.3.8). Registering a key that exists returns false and writes nothing; when
// another transaction holds the same key not yet committed, the call waits for it, so a race has one winner.
public interface IdempotencyStore {

    Optional<UUID> find(UUID tenantId, String key);

    boolean register(UUID tenantId, String key, String resourceType, UUID resourceId);
}
