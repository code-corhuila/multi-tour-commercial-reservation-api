package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogSettingsRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.policy.RepositoryCollaboratorEditing;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogSettings;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// HU-COM-001 T-05 (RF-004): the tenant_catalog_settings table read and written as the application user. A tenant without a row has the flag off.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogSettingsPersistenceTest {
    private static final Instant FIRST = Instant.parse("2026-10-30T12:00:00.123456Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcCatalogSettingsRepository repository;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
        repository = new JdbcCatalogSettingsRepository(jdbc);
    }

    @Test void aTenantWithoutARowHasNothingStoredAndTheFlagOff() {
        assertThat(repository.find(tenant)).isEmpty();
        assertThat(new RepositoryCollaboratorEditing(repository).allowsDescriptiveUpdates(tenant)).isFalse();
    }

    @Test void theRowRoundTripsTheFlagTheAuthorAndTheInstant() {
        repository.save(tenant, new CatalogSettings(true, FIRST, actor));
        assertThat(repository.find(tenant)).contains(new CatalogSettings(true, FIRST.truncatedTo(ChronoUnit.MICROS), actor));
        assertThat(new RepositoryCollaboratorEditing(repository).allowsDescriptiveUpdates(tenant)).isTrue();
    }

    @Test void savingAgainUpdatesTheSingleRowOfTheTenantAndKeepsItsCreationInstant() {
        repository.save(tenant, new CatalogSettings(true, FIRST, actor));
        var other = UUID.randomUUID();
        var later = FIRST.plusSeconds(60);
        repository.save(tenant, new CatalogSettings(false, later, other));
        assertThat(repository.find(tenant)).contains(new CatalogSettings(false, later.truncatedTo(ChronoUnit.MICROS), other));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.tenant_catalog_settings WHERE tenant_id = ?",
                Integer.class, tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT created_at = ? FROM commercial_reservation.tenant_catalog_settings WHERE tenant_id = ?",
                Boolean.class, java.sql.Timestamp.from(FIRST.truncatedTo(ChronoUnit.MICROS)), tenant)).isTrue();
    }

    @Test void theFlagOfOneTenantNeverReachesAnother() {
        repository.save(tenant, new CatalogSettings(true, FIRST, actor));
        assertThat(repository.find(UUID.randomUUID())).isEmpty();
    }
}
