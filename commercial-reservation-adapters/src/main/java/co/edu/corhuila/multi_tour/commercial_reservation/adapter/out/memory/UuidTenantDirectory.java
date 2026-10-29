package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.memory;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.Optional;
import java.util.UUID;

// Interim adapter (DD-2): reads the public identifier as the tenant UUID. The directory over the tenant copy replaces it.
public final class UuidTenantDirectory implements TenantDirectory {

    @Override
    public Optional<TenantRef> resolve(String identifier) {
        try {
            return Optional.of(new TenantRef(UUID.fromString(identifier), true));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
