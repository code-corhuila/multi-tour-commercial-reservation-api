package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPublicEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import java.util.UUID;

// Unlike the management read (HU-COM-002 RN-04), an inactive association is not found here (RN-05).
public final class GetPublicEstablishmentUseCase implements GetPublicEstablishment {

    private static final String PUBLIC_ROUTE = "/api/v1/commercial-reservation/public/tenants/";

    private final TenantDirectory directory;
    private final AssociatedEstablishmentRepository repository;

    public GetPublicEstablishmentUseCase(TenantDirectory directory, AssociatedEstablishmentRepository repository) {
        this.directory = directory;
        this.repository = repository;
    }

    @Override
    public PublicEstablishment get(String tenantIdentifier, UUID associationId) {
        UUID tenantId = PublicTenants.require(directory, tenantIdentifier);
        return repository.findByTenantAndId(tenantId, associationId)
                .filter(item -> item.tenantId().equals(tenantId) && item.associationStatus() == AssociationStatus.ACTIVE)
                .map(item -> project(tenantIdentifier, item)).orElseThrow(PublicOfferNotFoundException::new);
    }

    // The image address is the existing public route (AC-06.17), built with the identifier the visitor used.
    static PublicEstablishment project(String tenantIdentifier, AssociatedEstablishment item) {
        return new PublicEstablishment(item.associationId(), item.establishmentType(), item.commercialName(),
                item.commercialInformation(), PUBLIC_ROUTE + tenantIdentifier + "/associated-establishments/"
                        + item.associationId() + "/image");
    }
}
