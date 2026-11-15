package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-006 plan D-05 / DP-15 on the real schema: the name search runs inside the query, so the total and the page agree.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogNameSearchPersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcCatalogServiceRepository repository;
    private Instant clock = Instant.parse("2026-10-09T12:00:00Z");

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        DriverManagerDataSource source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        repository = new JdbcCatalogServiceRepository(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)));
    }

    private CatalogService plant(UUID owner, String name, CatalogServiceType type) {
        clock = clock.plusSeconds(60);
        CatalogService item = CatalogService.register(UUID.randomUUID(), owner, type, name,
                CommercialTerms.of(1_000_000L, 500_000L, type == CatalogServiceType.TOUR ? 10 : null, false, false), clock);
        repository.save(item);
        return item;
    }

    private CatalogPage search(UUID owner, String term, int page, int limit) {
        return repository.pageByTenant(owner, Optional.of(CatalogStatus.ACTIVE), Optional.of(CatalogServiceType.TOUR),
                Optional.ofNullable(term), page, limit);
    }

    @Test
    void theSearchIsACaseInsensitiveContainsOnTheCommercialNameNewestFirst() {
        plant(tenant, "Canyon at dawn", CatalogServiceType.TOUR);
        plant(tenant, "Coffee farm", CatalogServiceType.TOUR);
        plant(tenant, "CANYON at night", CatalogServiceType.TOUR);
        plant(tenant, "The great Canyon", CatalogServiceType.TOUR);

        CatalogPage found = search(tenant, "cAnYoN", 1, 20);

        assertThat(found.items()).extracting(CatalogService::commercialName)
                .containsExactly("The great Canyon", "CANYON at night", "Canyon at dawn");
        assertThat(found.total()).isEqualTo(3);
    }

    @Test
    void theTotalCountsTheMatchesAndThePagesSliceThem() {
        for (int index = 0; index < 5; index++) {
            plant(tenant, "Canyon " + index, CatalogServiceType.TOUR);
        }
        plant(tenant, "Coffee", CatalogServiceType.TOUR);

        CatalogPage second = search(tenant, "canyon", 2, 2);

        assertThat(second.total()).isEqualTo(5);
        assertThat(second.items()).extracting(CatalogService::commercialName).containsExactly("Canyon 2", "Canyon 1");
    }

    @Test
    void theSearchStaysInsideTheTenantTheStatusAndTheType() {
        plant(UUID.randomUUID(), "Canyon of another tenant", CatalogServiceType.TOUR);
        plant(tenant, "Canyon transport", CatalogServiceType.TRANSPORTATION);
        CatalogService inactive = plant(tenant, "Canyon inactive", CatalogServiceType.TOUR);
        inactive.inactivate(UUID.randomUUID(), clock.plusSeconds(1));
        repository.save(inactive);
        plant(tenant, "Canyon active", CatalogServiceType.TOUR);

        assertThat(search(tenant, "canyon", 1, 20).items()).extracting(CatalogService::commercialName)
                .containsExactly("Canyon active");
    }

    @Test
    void wildcardsAndQuotesInTheTermAreLiteralText() {
        plant(tenant, "Canyon 100% fun", CatalogServiceType.TOUR);
        plant(tenant, "Canyon plain", CatalogServiceType.TOUR);
        plant(tenant, "Rock_climb", CatalogServiceType.TOUR);
        plant(tenant, "Rockxclimb", CatalogServiceType.TOUR);

        assertThat(search(tenant, "%", 1, 20).items()).extracting(CatalogService::commercialName)
                .containsExactly("Canyon 100% fun");
        assertThat(search(tenant, "k_c", 1, 20).items()).extracting(CatalogService::commercialName)
                .containsExactly("Rock_climb");
        assertThat(search(tenant, "x'; DROP TABLE commercial_reservation.catalog_service; --", 1, 20).total()).isZero();
        assertThat(search(tenant, null, 1, 20).total()).isEqualTo(4);
    }

    @Test
    void aTermWithNoMatchIsAnEmptyPage() {
        plant(tenant, "Canyon", CatalogServiceType.TOUR);

        CatalogPage none = search(tenant, "volcano", 1, 20);

        assertThat(none.items()).isEmpty();
        assertThat(none.total()).isZero();
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
