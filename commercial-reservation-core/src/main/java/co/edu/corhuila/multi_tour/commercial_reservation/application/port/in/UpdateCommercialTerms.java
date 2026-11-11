package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import java.util.UUID;

// Rates, costs, capacity, transport and risk: the intent the HTTP adapter reserves for the Administrator.
public interface UpdateCommercialTerms {

    CatalogService update(UUID tenantId, UUID serviceId, CommercialTermsChange change);
}
