package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.UUID;

// The only intent the limited Operational Collaborator may be given, when the tenant enables it (RN-04).
public interface UpdateDescriptiveInformation {

    CatalogService update(UUID tenantId, UUID serviceId, String commercialName);
}
