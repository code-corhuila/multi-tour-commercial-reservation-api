package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The Administrator manages what is inactive, so this listing never hides it (plan D-04).
public interface ListCatalogServicesForManagement {

    List<CatalogService> list(UUID tenantId, Optional<CatalogStatus> status);
}
