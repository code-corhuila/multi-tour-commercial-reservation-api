package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicDeparture;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourDetail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PublicOfferNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// The detail of one offered tour with the departures that can be booked (plan D-02, D-07, RN-02). Any item that is
// not an active tour of the tenant of the link answers the same not found (RN-09, AC-06.11).
public final class GetPublicTourUseCase implements GetPublicTour {
    private final TenantDirectory directory;
    private final CatalogServiceRepository repository;
    private final PublicTourProjection projection;
    private final DepartureReader departures;
    private final Clock clock;

    public GetPublicTourUseCase(TenantDirectory directory, CatalogServiceRepository repository,
            QuoteDiscountBreakdown quote, DepartureReader departures, Clock clock) {
        this.directory = directory;
        this.repository = repository;
        this.projection = new PublicTourProjection(quote, clock);
        this.departures = departures;
        this.clock = clock;
    }

    @Override
    public PublicTourDetail get(String tenantIdentifier, UUID serviceId) {
        UUID tenantId = PublicTenants.require(directory, tenantIdentifier);
        PublicTour tour = repository.findByTenantAndId(tenantId, serviceId)
                .filter(item -> PublicTourProjection.isOffered(item, tenantId)).map(projection::project)
                .orElseThrow(PublicOfferNotFoundException::new);
        return new PublicTourDetail(tour.serviceId(), tour.commercialName(), tour.transportIncluded(),
                tour.baseValueCents(), tour.finalValueCents(), tour.discountPercentageBp(), tour.maxTravelers(),
                bookable(tenantId, serviceId));
    }

    // The reader already filters, but the rule is checked again here: the adapter is not the only guard (RN-02).
    private List<PublicDeparture> bookable(UUID tenantId, UUID serviceId) {
        Instant now = clock.instant();
        return departures.findOpen(tenantId, serviceId, now).stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.serviceId().equals(serviceId))
                .filter(row -> row.status() == DepartureStatus.SCHEDULED && row.startsAt().isAfter(now)
                        && row.seats().available() > 0)
                .sorted(Comparator.comparing(DepartureState::startsAt))
                .map(row -> new PublicDeparture(row.departureId(), row.startsAt(), row.seats().available())).toList();
    }
}
