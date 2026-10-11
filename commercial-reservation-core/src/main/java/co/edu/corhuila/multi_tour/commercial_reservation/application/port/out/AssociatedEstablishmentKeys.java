package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public interface AssociatedEstablishmentKeys {
    // Atomically resolve a tenant-scoped key or run creation once. The key, callback's repository
    // writes and pending events must share a transaction; any failure rolls all of them back.
    EstablishmentRegistration register(UUID tenantId, String key, Supplier<AssociatedEstablishment> create,
            Function<UUID, AssociatedEstablishment> lookup);
}
