package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// RN-SAL-001 and H8 (ND-08, DP-D6-8): a change of the tour capacity recomputes the effective capacity of the open departures of the
// service, in the same use case: max(min(tour capacity, vehicle total), held seats). Below the held seats it is refused.
class EffectiveCapacityRecalculationTest {
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID service = UUID.randomUUID();
    private final InMemoryCatalogServiceRepository repository = new InMemoryCatalogServiceRepository();
    private final Departures departures = new Departures();
    private final UpdateCommercialTermsUseCase update = new UpdateCommercialTermsUseCase(repository,
            Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), departures::maxHeld, departures);

    private void tourOf(int capacity) {
        repository.plant(CatalogService.register(service, tenant, CatalogServiceType.TOUR, "Recalculated tour",
                CommercialTerms.of(100L, 50L, capacity, false, false), NOW));
    }

    private UUID departure(DepartureStatus status, int effective, int held, Integer vehicles) {
        UUID id = UUID.randomUUID();
        departures.rows.put(id, new DepartureState(id, tenant, service, NOW.plusSeconds(86_400), status, effective, held, null,
                vehicles));
        return id;
    }

    private static CommercialTermsChange capacity(int value) {
        return new CommercialTermsChange(null, null, value, null, null);
    }

    @Test
    void aLowerTourCapacityLowersTheEffectiveCapacityAndStopsTheSaleAboveIt() {
        tourOf(20);
        UUID id = departure(DepartureStatus.SCHEDULED, 20, 10, 20);

        update.update(tenant, service, capacity(15));

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(15);
        assertThat(departures.rows.get(id).heldSeats()).isEqualTo(10);
        assertThat(departures.rows.get(id).seats().available()).isEqualTo(5);
    }

    @Test
    void aHigherTourCapacityRaisesTheEffectiveCapacityUpToTheVehicleTotal() {
        tourOf(15);
        UUID id = departure(DepartureStatus.SCHEDULED, 15, 10, 20);

        update.update(tenant, service, capacity(18));
        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(18);

        update.update(tenant, service, capacity(30));
        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(20);
    }

    @Test
    void aTourCapacityBelowTheHeldSeatsIsRefusedAndNothingIsWritten() {
        tourOf(20);
        UUID id = departure(DepartureStatus.SCHEDULED, 20, 10, 20);

        assertThatThrownBy(() -> update.update(tenant, service, capacity(9)))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("9").hasMessageContaining("10")
                .hasMessageContaining("held");

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(20);
        assertThat(departures.saves).isZero();
        assertThat(repository.findByTenantAndId(tenant, service).orElseThrow().terms().configuredCapacity()).isEqualTo(20);
    }

    @Test
    void aDepartureWithoutAKnownVehicleTotalKeepsItsEffectiveCapacity() {
        tourOf(20);
        UUID id = departure(DepartureStatus.SCHEDULED, 20, 10, null);

        update.update(tenant, service, capacity(15));
        update.update(tenant, service, capacity(30));

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(20);
        assertThat(departures.saves).isZero();
    }

    @Test
    void theEffectiveCapacityNeverFallsBelowTheHeldSeatsEvenWithASmallVehicleTotal() {
        tourOf(20);
        UUID id = departure(DepartureStatus.SCHEDULED, 12, 10, 8);

        update.update(tenant, service, capacity(15));

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(10);
    }

    @Test
    void everyOpenDepartureOfTheServiceIsRecalculatedAndTheFinalOnesAreNot() {
        tourOf(20);
        UUID scheduled = departure(DepartureStatus.SCHEDULED, 20, 2, 20);
        UUID running = departure(DepartureStatus.IN_EXECUTION, 20, 4, 20);
        UUID finalized = departure(DepartureStatus.FINALIZED, 20, 3, 20);
        UUID cancelled = departure(DepartureStatus.CANCELLED, 20, 0, 20);

        update.update(tenant, service, capacity(15));

        assertThat(departures.rows.get(scheduled).effectiveCapacity()).isEqualTo(15);
        assertThat(departures.rows.get(running).effectiveCapacity()).isEqualTo(15);
        assertThat(departures.rows.get(finalized).effectiveCapacity()).isEqualTo(20);
        assertThat(departures.rows.get(cancelled).effectiveCapacity()).isEqualTo(20);
    }

    @Test
    void aRequestThatRepeatsTheCurrentCapacityRealignsADriftedDeparture() {
        tourOf(15);
        UUID id = departure(DepartureStatus.SCHEDULED, 20, 10, 20);

        update.update(tenant, service, capacity(15));

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(15);
    }

    @Test
    void aChangeWithoutCapacityDoesNotTouchTheDepartures() {
        tourOf(20);
        departure(DepartureStatus.SCHEDULED, 20, 10, 20);

        update.update(tenant, service, new CommercialTermsChange(200L, null, null, null, null));

        assertThat(departures.locks).isZero();
        assertThat(departures.saves).isZero();
    }

    @Test
    void theDeparturesOfAnotherServiceAreNotTouched() {
        tourOf(20);
        UUID id = UUID.randomUUID();
        departures.rows.put(id, new DepartureState(id, tenant, UUID.randomUUID(), NOW.plusSeconds(86_400),
                DepartureStatus.SCHEDULED, 20, 0, null, 20));

        update.update(tenant, service, capacity(15));

        assertThat(departures.rows.get(id).effectiveCapacity()).isEqualTo(20);
    }

    private static final class Departures implements ServiceDepartures {
        final Map<UUID, DepartureState> rows = new LinkedHashMap<>();
        int locks;
        int saves;

        int maxHeld(UUID tenantId, UUID serviceId) {
            return rows.values().stream().filter(row -> row.serviceId().equals(serviceId))
                    .mapToInt(DepartureState::heldSeats).max().orElse(0);
        }

        @Override
        public List<DepartureState> lockAllOf(UUID tenantId, UUID serviceId) {
            locks++;
            return rows.values().stream()
                    .filter(row -> row.tenantId().equals(tenantId) && row.serviceId().equals(serviceId)).toList();
        }

        @Override
        public void save(DepartureState state) {
            saves++;
            rows.put(state.departureId(), state);
        }
    }
}
