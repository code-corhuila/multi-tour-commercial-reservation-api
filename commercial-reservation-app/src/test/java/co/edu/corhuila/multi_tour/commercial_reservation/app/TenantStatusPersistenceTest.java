package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ApplyTenantEventUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEventType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// api spec 021 T-02, T-04, T-05 over the real schema (db spec 021): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class TenantStatusPersistenceTest {
    private static final Instant T0 = Instant.parse("2026-10-29T14:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcTenantStatusStore store;
    private JdbcProcessedEvents processed;
    private JdbcTenantEventTransaction transaction;
    private ApplyTenantEventUseCase apply;
    private final UUID tenant = UUID.randomUUID();
    private final String identifier = "t-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER")
                && present("TEST_DATABASE_PASSWORD"), "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        store = new JdbcTenantStatusStore(jdbc);
        processed = new JdbcProcessedEvents(jdbc, Clock.fixed(T0, ZoneOffset.UTC));
        transaction = new JdbcTenantEventTransaction(new TransactionTemplate(new DataSourceTransactionManager(source)));
        apply = new ApplyTenantEventUseCase(store, processed, transaction);
    }

    private static boolean present(String name) {
        return System.getenv(name) != null && !System.getenv(name).isBlank();
    }

    private TenantEvent created(UUID id, UUID tenantId, String name, Instant at) {
        return new TenantEvent(id, TenantEventType.TENANT_CREATED, tenantId, at, name, TenantStatus.ACTIVE,
                "America/Bogota", "CO");
    }

    private TenantEvent inactivated(UUID id, Instant at) {
        return new TenantEvent(id, TenantEventType.TENANT_INACTIVATED, tenant, at, null, null, null, null);
    }

    private String statusRow() {
        return jdbc.queryForObject("SELECT status FROM commercial_reservation.tenant_status WHERE tenant_id = ?",
                String.class, tenant);
    }

    private boolean idRecorded(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.processed_event WHERE event_id = ?",
                Integer.class, id) == 1;
    }

    @Test
    void copyAndProcessedIdAreWrittenTogether() {
        UUID id = UUID.randomUUID();

        assertThat(apply.apply(created(id, tenant, identifier, T0))).isEqualTo(TenantEventOutcome.APPLIED);
        assertThat(statusRow()).isEqualTo("ACTIVE");
        assertThat(idRecorded(id)).isTrue();
        assertThat(apply.apply(created(id, tenant, identifier, T0))).isEqualTo(TenantEventOutcome.NOT_APPLIED);

        UUID failing = UUID.randomUUID();
        ProcessedEvents breaking = new ProcessedEvents() {
            public boolean wasProcessed(UUID eventId) { return processed.wasProcessed(eventId); }
            public void record(UUID eventId) {
                processed.record(eventId);
                throw new IllegalStateException("forced after the id");
            }
        };
        var broken = new ApplyTenantEventUseCase(store, breaking, transaction);
        assertThatThrownBy(() -> broken.apply(inactivated(failing, T0.plusSeconds(10)))).isInstanceOf(IllegalStateException.class);
        assertThat(idRecorded(failing)).isFalse();
        assertThat(statusRow()).isEqualTo("ACTIVE");
    }

    @Test
    void anOlderEventLeavesTheNewerCopy() {
        apply.apply(created(UUID.randomUUID(), tenant, identifier, T0));
        UUID newer = UUID.randomUUID();
        UUID older = UUID.randomUUID();

        assertThat(apply.apply(inactivated(newer, T0.plusSeconds(60)))).isEqualTo(TenantEventOutcome.APPLIED);
        assertThat(apply.apply(inactivated(older, T0.plusSeconds(30)))).isEqualTo(TenantEventOutcome.NOT_APPLIED);

        assertThat(statusRow()).isEqualTo("INACTIVE");
        assertThat(idRecorded(older)).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_event_occurred_at FROM commercial_reservation.tenant_status "
                + "WHERE tenant_id = ?", java.sql.Timestamp.class, tenant).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void theIdentifierIsUniqueAmongTenants() {
        apply.apply(created(UUID.randomUUID(), tenant, identifier, T0));

        assertThatThrownBy(() -> apply.apply(created(UUID.randomUUID(), UUID.randomUUID(), identifier, T0)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theDirectoryResolvesTheIdentifierFromTheCopy() {
        apply.apply(created(UUID.randomUUID(), tenant, identifier, T0));
        var directory = new JdbcTenantStatusDirectory(jdbc);

        assertThat(directory.resolve(identifier)).hasValueSatisfying(ref -> {
            assertThat(ref.tenantId()).isEqualTo(tenant);
            assertThat(ref.active()).isTrue();
        });
        apply.apply(inactivated(UUID.randomUUID(), T0.plusSeconds(5)));
        assertThat(directory.resolve(identifier)).hasValueSatisfying(ref -> assertThat(ref.active()).isFalse());
        assertThat(directory.resolve("t-no-such-tenant")).isEmpty();
        assertThat(new JdbcTenantStatusReader(jdbc).statusOf(tenant)).contains(TenantStatus.INACTIVE);
        assertThat(new JdbcTenantStatusReader(jdbc).statusOf(UUID.randomUUID())).isEmpty();
    }
}
