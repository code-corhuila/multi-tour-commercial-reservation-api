package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// ND-08, DP-D6-8: the mirror keeps the vehicle total of the last SalidaVehiclesChanged it applied, so a later change of the tour
// capacity can recompute the effective capacity (RN-SAL-001, H8).
class MirrorVehicleTotalTest extends PublicOfferFixture {
    private static final Instant STARTS = Instant.parse("2026-10-20T08:00:00Z");
    private static final Instant OCCURRED = Instant.parse("2026-11-03T10:00:00Z");
    private static final UUID DEPARTURE = new UUID(7, 1);

    private final DepartureFakes store = new DepartureFakes();
    private final MirrorSalidaStateUseCase mirror = new MirrorSalidaStateUseCase(store, store, store, repository);
    private long seq = 1;

    private MirrorOutcome scheduled(CatalogService tour) {
        return mirror.apply(new SalidaEvent.Scheduled(new UUID(8, seq++), tour.tenantId(), DEPARTURE, tour.serviceId(), STARTS,
                OCCURRED.plusSeconds(seq)));
    }

    private MirrorOutcome vehicles(int total, long seconds) {
        return mirror.apply(new SalidaEvent.VehiclesChanged(new UUID(8, seq++), TENANT, DEPARTURE, total, OCCURRED.plusSeconds(seconds)));
    }

    private DepartureState row() {
        return store.rows.get(DEPARTURE);
    }

    @Test
    void aNewDepartureHasNoKnownVehicleTotal() {
        scheduled(plantTourWith(TENANT, "Canyon", 100_000, 18, true));

        assertThat(row().vehicleCapacityCeiling()).isNull();
    }

    @Test
    void theVehicleEventStoresTheTotalNextToTheEffectiveCapacity() {
        scheduled(plantTourWith(TENANT, "Canyon", 100_000, 18, true));

        vehicles(12, 100);

        assertThat(row().vehicleCapacityCeiling()).isEqualTo(12);
        assertThat(row().effectiveCapacity()).isEqualTo(12);
    }

    @Test
    void theTotalIsKeptEvenWhenTheTourCapacityIsTheLowerBound() {
        scheduled(plantTourWith(TENANT, "Canyon", 100_000, 18, true));

        vehicles(40, 100);

        assertThat(row().vehicleCapacityCeiling()).isEqualTo(40);
        assertThat(row().effectiveCapacity()).isEqualTo(18);
    }

    @Test
    void aNewerEventReplacesTheTotalAndAnOlderOneDoesNot() {
        scheduled(plantTourWith(TENANT, "Canyon", 100_000, 18, true));
        vehicles(12, 100);

        assertThat(vehicles(30, 50)).isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);
        assertThat(row().vehicleCapacityCeiling()).isEqualTo(12);

        assertThat(vehicles(16, 200)).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(row().vehicleCapacityCeiling()).isEqualTo(16);
        assertThat(row().effectiveCapacity()).isEqualTo(16);
    }

    @Test
    void theStatusEventsKeepTheTotal() {
        scheduled(plantTourWith(TENANT, "Canyon", 100_000, 18, true));
        vehicles(12, 100);

        mirror.apply(new SalidaEvent.ExecutionStarted(new UUID(8, seq++), TENANT, DEPARTURE, OCCURRED.plusSeconds(300)));

        assertThat(row().vehicleCapacityCeiling()).isEqualTo(12);
    }
}
