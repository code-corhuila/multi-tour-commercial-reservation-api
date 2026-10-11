package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.UUID;

// Resolves active and inactive items alike: a sold item must stay resolvable forever (plan D-04).
public interface GetCatalogService {

    CatalogService get(UUID tenantId, UUID serviceId);
}
