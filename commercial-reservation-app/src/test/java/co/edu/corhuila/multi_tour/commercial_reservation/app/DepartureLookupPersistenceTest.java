package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-007 api plan D-05: the departure lookup of the creation check reads the salida_capacity projection by tenant and
// departure, reads only, and keeps every status so the use case can refuse the ones that cannot be reserved.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DepartureLookupPersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcDepartureReader reader;
    private JdbcDepartureStateRepository departures;
    private CatalogService tour;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        var catalog = new JdbcCatalogServiceRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)));
        reader = new JdbcDepartureReader(jdbc);
        departures = new JdbcDepartureStateRepository(jdbc);
        tour = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Canyon",
                CommercialTerms.of(10_000_000L, 4_000_000L, 12, false, false), Instant.now());
        catalog.save(tour);
    }

    private DepartureState row(DepartureStatus status, int capacity, int held) {
        DepartureState state = new DepartureState(UUID.randomUUID(), tenant, tour.serviceId(),
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(24, ChronoUnit.HOURS), status, capacity, held);
        departures.save(state);
        return state;
    }

    @Test
    void theLookupReturnsTheServiceStartStatusAndSeatsOfTheDeparture() {
        DepartureState stored = row(DepartureStatus.SCHEDULED, 10, 3);

        assertThat(reader.find(tenant, stored.departureId())).contains(stored);
    }

    @Test
    void theLookupIsEmptyForAnotherTenantAndForAnUnknownDeparture() {
        DepartureState stored = row(DepartureStatus.SCHEDULED, 10, 0);

        assertThat(reader.find(UUID.randomUUID(), stored.departureId())).isEmpty();
        assertThat(reader.find(tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theLookupKeepsTheDeparturesThatAreNotScheduledAndTheFullOnes() {
        for (DepartureStatus status : DepartureStatus.values()) {
            DepartureState stored = row(status, 5, 0);
            assertThat(reader.find(tenant, stored.departureId()).orElseThrow().status()).isEqualTo(status);
        }
        DepartureState full = row(DepartureStatus.SCHEDULED, 4, 4);
        assertThat(reader.find(tenant, full.departureId()).orElseThrow().seats().available()).isZero();
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
