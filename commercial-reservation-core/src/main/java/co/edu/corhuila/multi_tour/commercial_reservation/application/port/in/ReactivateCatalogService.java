package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.UUID;

public interface ReactivateCatalogService {

    CatalogService reactivate(UUID tenantId, UUID serviceId, UUID actorId);
}
