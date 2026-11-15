package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-007 DP-03: the per-person transport fare is the transport_rate_per_person_cents column of the catalog service of the
// tour, read by tenant and service. A NULL column is the empty answer (not parametrized, RN-15); another tenant's row never answers.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class TransportRatePersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcCatalogServiceRepository catalog;
    private JdbcTransportRateReader reader;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"), "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        catalog = new JdbcCatalogServiceRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)));
        reader = new JdbcTransportRateReader(jdbc);
    }

    private UUID tour(Long rate) {
        UUID id = UUID.randomUUID();
        catalog.save(CatalogService.register(id, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(10_000_000L, 4_000_000L, 12, true, false, rate), Instant.now()));
        return id;
    }

    @Test
    void theRateOfTheCatalogServiceIsReadInCents() {
        UUID tour = tour(2_500_000L);

        assertThat(reader.perPersonCents(tenant, tour, UUID.randomUUID())).contains(new Cents(2_500_000L));
    }

    @Test
    void aZeroRateIsAParametrizedRateAndANullColumnIsNot() {
        assertThat(reader.perPersonCents(tenant, tour(0L), UUID.randomUUID())).contains(new Cents(0L));
        assertThat(reader.perPersonCents(tenant, tour(null), UUID.randomUUID())).isEmpty();
    }

    @Test
    void anotherTenantAndAnUnknownTourAreTheEmptyAnswer() {
        UUID tour = tour(2_500_000L);

        assertThat(reader.perPersonCents(UUID.randomUUID(), tour, UUID.randomUUID())).isEmpty();
        assertThat(reader.perPersonCents(tenant, UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
