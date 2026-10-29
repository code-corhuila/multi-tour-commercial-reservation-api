package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Shared by the HU-COM-006 core tests: a directory fake and planting helpers for the public offer.
abstract class PublicOfferFixture extends UseCaseFixture {

    static final String IDENTIFIER = TENANT.toString();
    static final String OTHER_IDENTIFIER = OTHER_TENANT.toString();
    static final String INACTIVE_IDENTIFIER = "00000000-0000-0000-0000-0000000000b3";

    final FakeTenantDirectory directory = new FakeTenantDirectory();

    PublicOfferFixture() {
        directory.put(IDENTIFIER, TENANT, true);
        directory.put(OTHER_IDENTIFIER, OTHER_TENANT, true);
        directory.put(INACTIVE_IDENTIFIER, new UUID(0, 0xb3), false);
    }

    CatalogService plantTourWith(UUID tenant, String name, long rateCents, int capacity, boolean transportRequired) {
        clock.advanceSeconds(60);
        CatalogService tour = CatalogService.register(ids.newId(), tenant, CatalogServiceType.TOUR, name,
                CommercialTerms.of(rateCents, rateCents / 2, capacity, transportRequired, false), clock.instant());
        repository.plant(tour);
        return tour;
    }

    CatalogService plantTransport(UUID tenant, String name) {
        clock.advanceSeconds(60);
        CatalogService transport = CatalogService.register(ids.newId(), tenant, CatalogServiceType.TRANSPORTATION,
                name, CommercialTerms.of(5_000_000L, 2_000_000L, 40, false, false), clock.instant());
        repository.plant(transport);
        return transport;
    }

    static final class FakeTenantDirectory implements TenantDirectory {
        private final Map<String, TenantRef> known = new HashMap<>();
        int lookups;

        void put(String identifier, UUID tenantId, boolean active) {
            known.put(identifier, new TenantRef(tenantId, active));
        }

        @Override
        public Optional<TenantRef> resolve(String identifier) {
            lookups++;
            return Optional.ofNullable(known.get(identifier));
        }
    }
}
