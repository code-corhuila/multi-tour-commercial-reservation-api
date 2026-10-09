package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.Optional;
import java.util.UUID;

// The Administrator manages what is inactive, so this listing never hides it (plan D-04).
public interface ListCatalogServicesForManagement {

    CatalogPage list(UUID tenantId, Optional<CatalogStatus> status, Optional<CatalogServiceType> type,
            int page, int limit);
}
