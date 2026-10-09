package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

abstract class UseCaseFixture {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final Instant START = Instant.parse("2026-10-08T10:00:00Z");

    final InMemoryCatalogServiceRepository repository = new InMemoryCatalogServiceRepository();
    final SteppingClock clock = new SteppingClock(START);
    final IdGenerator ids = new SequentialIds();

    final RegisterCatalogServiceUseCase register = new RegisterCatalogServiceUseCase(repository, ids, clock);
    final UpdateCommercialTermsUseCase updateTerms = new UpdateCommercialTermsUseCase(repository, clock);
    final UpdateDescriptiveInformationUseCase updateDescription =
            new UpdateDescriptiveInformationUseCase(repository, clock);
    final InactivateCatalogServiceUseCase inactivate = new InactivateCatalogServiceUseCase(repository, clock);
    final ReactivateCatalogServiceUseCase reactivate = new ReactivateCatalogServiceUseCase(repository, clock);
    final GetCatalogServiceUseCase get = new GetCatalogServiceUseCase(repository);
    final ListCatalogServicesForManagementUseCase listForManagement =
            new ListCatalogServicesForManagementUseCase(repository);
    final ListOfferedCatalogServicesUseCase listOffered = new ListOfferedCatalogServicesUseCase(repository);

    // Planted straight into the repository so a test of one use case never depends on another one working.
    CatalogService plantTour(UUID tenant, String name) {
        clock.advanceSeconds(60);
        CatalogService tour = CatalogService.register(ids.newId(), tenant, CatalogServiceType.TOUR, name,
                CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, false), clock.instant());
        repository.plant(tour);
        return tour;
    }

    CatalogService plantInactiveTour(UUID tenant, String name) {
        CatalogService tour = plantTour(tenant, name);
        tour.inactivate(ACTOR, clock.instant());
        tour.pullEvents();
        repository.plant(tour);
        return tour;
    }

    RegisterCatalogService.Command tourCommand(Long rateCents, Long costCents, Integer capacity) {
        return new RegisterCatalogService.Command(TENANT, CatalogServiceType.TOUR, "Chicamocha Canyon", rateCents,
                costCents, capacity, true, false);
    }

    static final class SteppingClock extends Clock {
        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class SequentialIds implements IdGenerator {
        private final AtomicLong next = new AtomicLong(1);

        @Override
        public UUID newId() {
            return new UUID(0, next.getAndIncrement());
        }
    }
}
