package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import java.util.UUID;

public record CatalogServiceResponse(UUID serviceId, CatalogServiceType serviceType, String commercialName,
        CatalogStatus catalogStatus, long baseRateCents, long baseOperationalCostCents, Integer configuredCapacity,
        boolean transportRequired, boolean highRisk, String createdAt, String updatedAt) {
    public static CatalogServiceResponse from(CatalogService item) {
        var terms = item.terms();
        return new CatalogServiceResponse(item.serviceId(), item.serviceType(), item.commercialName(),
                item.catalogStatus(), terms.baseRate().amount(), terms.baseOperationalCost().amount(),
                terms.configuredCapacity(), terms.transportRequired(), terms.highRisk(),
                item.createdAt().toString(), item.updatedAt().toString());
    }
}
