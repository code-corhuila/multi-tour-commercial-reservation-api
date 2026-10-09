package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import java.util.UUID;

public interface RegisterCatalogService {

    CatalogService register(Command command);

    // Money and capacity arrive boxed so a missing value reaches the domain and is named there (AC-01.2).
    record Command(UUID tenantId, CatalogServiceType serviceType, String commercialName, Long baseRateCents,
            Long baseOperationalCostCents, Integer configuredCapacity, boolean transportRequired,
            boolean highRisk) {
    }
}
