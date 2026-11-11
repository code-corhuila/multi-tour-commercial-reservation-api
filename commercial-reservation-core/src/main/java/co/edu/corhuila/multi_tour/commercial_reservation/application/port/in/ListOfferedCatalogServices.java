package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.List;
import java.util.UUID;

// What a new reservation may be built from: an inactive item is not offered (RN-02).
public interface ListOfferedCatalogServices {

    List<CatalogService> list(UUID tenantId);
}
