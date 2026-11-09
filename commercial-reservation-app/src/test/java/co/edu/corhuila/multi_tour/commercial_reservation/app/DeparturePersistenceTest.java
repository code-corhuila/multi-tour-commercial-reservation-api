package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.MirrorSalidaStateUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-006 T-04 and T-07 on the real schema: the departure reader, the mirror row, the processed event ids and the
// transaction around them (AC-06.3, AC-06.14, AC-06.16, RN-02, RN-11).
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DeparturePersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcCatalogServiceRepository catalog;
    private JdbcDepartureReader reader;
    private JdbcDepartureStateRepository departures;
    private JdbcProcessedEvents processed;
    private JdbcSalidaMirrorTransaction transaction;
    private CatalogService tour;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        DriverManagerDataSource source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        catalog = new JdbcCatalogServiceRepository(jdbc, transactions);
        reader = new JdbcDepartureReader(jdbc);
        departures = new JdbcDepartureStateRepository(jdbc);
        processed = new JdbcProcessedEvents(jdbc);
        transaction = new JdbcSalidaMirrorTransaction(transactions);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
        tour = tour(tenant, 12, false);
    }

    private CatalogService tour(UUID owner, int capacity, boolean transport) {
        CatalogService item = CatalogService.register(UUID.randomUUID(), owner, CatalogServiceType.TOUR, "Canyon",
                CommercialTerms.of(10_000_000L, 4_000_000L, capacity, transport, false), Instant.now());
        catalog.save(item);
        return item;
    }

    private DepartureState row(CatalogService service, DepartureStatus status, long hoursFromNow, int capacity, int held) {
        DepartureState state = new DepartureState(UUID.randomUUID(), service.tenantId(), service.serviceId(),
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(hoursFromNow, ChronoUnit.HOURS), status, capacity, held);
        departures.save(state);
        return state;
    }

    @Test
    void theReaderReturnsOnlyScheduledFutureDeparturesOfTheTourOrderedByStart() {
        DepartureState later = row(tour, DepartureStatus.SCHEDULED, 48, 10, 0);
        DepartureState sooner = row(tour, DepartureStatus.SCHEDULED, 24, 10, 3);
        DepartureState full = row(tour, DepartureStatus.SCHEDULED, 72, 10, 10);
        row(tour, DepartureStatus.SCHEDULED, -2, 10, 0);
        row(tour, DepartureStatus.CANCELLED, 30, 10, 0);
        row(tour, DepartureStatus.IN_EXECUTION, 31, 10, 0);
        row(tour, DepartureStatus.FINALIZED, 32, 10, 0);
        row(tour(tenant, 12, false), DepartureStatus.SCHEDULED, 24, 10, 0);
        row(tour(UUID.randomUUID(), 12, false), DepartureStatus.SCHEDULED, 24, 10, 0);

        var found = reader.findOpen(tenant, tour.serviceId(), Instant.now());

        assertThat(found).extracting(DepartureState::departureId)
                .containsExactly(sooner.departureId(), later.departureId(), full.departureId());
        assertThat(found).extracting(DepartureState::heldSeats).containsExactly(3, 0, 10);
    }

    @Test
    void theReaderTakesTheClockOfTheCallerAndNotTheClockOfTheDatabase() {
        DepartureState departure = row(tour, DepartureStatus.SCHEDULED, 24, 10, 0);

        assertThat(reader.findOpen(tenant, tour.serviceId(), departure.startsAt().plusSeconds(1))).isEmpty();
        assertThat(reader.findOpen(tenant, tour.serviceId(), departure.startsAt().minusSeconds(1)))
                .containsExactly(departure);
    }

    @Test
    void aSavedRowIsFoundByTenantAndDepartureAndNeverByAnotherTenant() {
        DepartureState state = row(tour, DepartureStatus.SCHEDULED, 24, 10, 0);

        assertThat(departures.find(tenant, state.departureId())).contains(state);
        assertThat(departures.find(UUID.randomUUID(), state.departureId())).isEmpty();
        assertThat(departures.find(tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void savingAgainUpdatesStatusAndCapacityButNeverTheHeldSeatsOfTheReservations() {
        DepartureState state = row(tour, DepartureStatus.SCHEDULED, 24, 10, 0);
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = 4 WHERE tenant_id = ? AND salida_id = ?",
                tenant, state.departureId());

        departures.save(state.withStatus(DepartureStatus.IN_EXECUTION).withEffectiveCapacity(7));

        DepartureState stored = departures.find(tenant, state.departureId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(DepartureStatus.IN_EXECUTION);
        assertThat(stored.effectiveCapacity()).isEqualTo(7);
        assertThat(stored.heldSeats()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.salida_capacity "
                + "WHERE tenant_id = ? AND salida_id = ?", Integer.class, tenant, state.departureId())).isEqualTo(1);
    }

    @Test
    void everyStatusRoundTrips() {
        for (DepartureStatus status : DepartureStatus.values()) {
            DepartureState state = row(tour, status, 24, 5, 0);
            assertThat(departures.find(tenant, state.departureId()).orElseThrow().status()).isEqualTo(status);
        }
    }

    @Test
    void anEventIdIsRecordedOnceAndASecondRecordIsRefusedByTheKey() {
        UUID eventId = UUID.randomUUID();
        assertThat(processed.wasProcessed(eventId)).isFalse();

        processed.record(eventId);

        assertThat(processed.wasProcessed(eventId)).isTrue();
        assertThatThrownBy(() -> processed.record(eventId)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theRowAndTheEventIdCommitTogether() {
        UUID eventId = UUID.randomUUID();
        DepartureState state = new DepartureState(UUID.randomUUID(), tenant, tour.serviceId(),
                Instant.now().plus(1, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0);

        transaction.inTransaction(() -> {
            departures.save(state);
            processed.record(eventId);
            return null;
        });

        assertThat(departures.find(tenant, state.departureId())).isPresent();
        assertThat(processed.wasProcessed(eventId)).isTrue();
    }

    @Test
    void aFailureRollsBackTheRowAndTheEventIdTogether() {
        UUID eventId = UUID.randomUUID();
        DepartureState state = new DepartureState(UUID.randomUUID(), tenant, tour.serviceId(),
                Instant.now().plus(1, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0);

        assertThatThrownBy(() -> transaction.inTransaction(() -> {
            departures.save(state);
            processed.record(eventId);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(departures.find(tenant, state.departureId())).isEmpty();
        assertThat(processed.wasProcessed(eventId)).isFalse();
    }

    @Test
    void theMirrorUseCaseOverTheRealSchemaAppliesAnEventOnceAndNeverMovesBackwards() {
        var mirror = new MirrorSalidaStateUseCase(departures, processed, transaction, catalog);
        UUID departureId = UUID.randomUUID();
        Instant startsAt = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(2, ChronoUnit.DAYS);
        var scheduled = new SalidaEvent.Scheduled(UUID.randomUUID(), tenant, departureId, tour.serviceId(), startsAt);

        assertThat(mirror.apply(scheduled)).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(mirror.apply(scheduled)).isEqualTo(MirrorOutcome.DUPLICATE);
        assertThat(mirror.apply(new SalidaEvent.Finalized(UUID.randomUUID(), tenant, departureId)))
                .isEqualTo(MirrorOutcome.APPLIED);
        assertThat(mirror.apply(new SalidaEvent.ExecutionStarted(UUID.randomUUID(), tenant, departureId)))
                .isEqualTo(MirrorOutcome.IGNORED_OUT_OF_ORDER);

        DepartureState stored = departures.find(tenant, departureId).orElseThrow();
        assertThat(stored.status()).isEqualTo(DepartureStatus.FINALIZED);
        assertThat(stored.startsAt()).isEqualTo(startsAt);
        assertThat(stored.effectiveCapacity()).isEqualTo(12);
    }

    @Test
    void aVehicleEventSetsTheLowerCapacityOfARequiredTransportTour() {
        var mirror = new MirrorSalidaStateUseCase(departures, processed, transaction, catalog);
        CatalogService withBus = tour(tenant, 12, true);
        UUID departureId = UUID.randomUUID();
        mirror.apply(new SalidaEvent.Scheduled(UUID.randomUUID(), tenant, departureId, withBus.serviceId(),
                Instant.now().plus(2, ChronoUnit.DAYS)));
        assertThat(departures.find(tenant, departureId).orElseThrow().effectiveCapacity()).isZero();

        mirror.apply(new SalidaEvent.VehiclesChanged(UUID.randomUUID(), tenant, departureId, 8));

        assertThat(departures.find(tenant, departureId).orElseThrow().effectiveCapacity()).isEqualTo(8);
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
