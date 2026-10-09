package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// No delete operation on purpose: physical deletion is not defined (RN-06). Persisting an item also
// persists the events it has pending, in the same transaction (plan D-07).
// Listings are unpaged until the HTTP adapter introduces page and limit (T-09).
public interface CatalogServiceRepository {

    void save(CatalogService service);

    Optional<CatalogService> findByTenantAndId(UUID tenantId, UUID serviceId);

    // Newest first. An empty filter returns every status.
    List<CatalogService> listByTenant(UUID tenantId, Optional<CatalogStatus> status);
}
