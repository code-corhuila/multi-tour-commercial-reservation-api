package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogPersistenceTest {
    private static final Instant CREATED = Instant.parse("2026-10-09T12:00:00.123456789Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private CatalogServiceRepository repository;
    private JdbcIdempotencyKeys keys;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        DriverManagerDataSource source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        repository = new JdbcCatalogServiceRepository(jdbc, transactions);
        keys = new JdbcIdempotencyKeys(jdbc);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
    }

    @Test
    void registeredItemRoundTripsEveryField() {
        for (CatalogServiceType type : CatalogServiceType.values()) {
            CommercialTerms terms = CommercialTerms.of(5_000_000_001L, 3_000_000_002L,
                    type == CatalogServiceType.TOUR ? 17 : null, type == CatalogServiceType.TOUR, true);
            CatalogService original = CatalogService.register(UUID.randomUUID(), tenant, type, "Generated item",
                    terms, CREATED);
            repository.save(original);
            CatalogService restored = repository.findByTenantAndId(tenant, original.serviceId()).orElseThrow();
            assertThat(restored.serviceId()).isEqualTo(original.serviceId());
            assertThat(restored.tenantId()).isEqualTo(tenant);
            assertThat(restored.serviceType()).isEqualTo(type);
            assertThat(restored.commercialName()).isEqualTo(original.commercialName());
            assertThat(restored.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
            assertThat(restored.terms()).isEqualTo(terms);
            assertThat(restored.createdAt()).isEqualTo(micros(CREATED));
            assertThat(restored.updatedAt()).isEqualTo(micros(CREATED));
            assertThat(restored.pullEvents()).isEmpty();
        }
    }

    @Test
    void updatingAnItemKeepsItsIdentifierAndCreationTime() {
        CatalogService original = item(UUID.randomUUID(), tenant, CREATED);
        repository.save(original);
        Instant updated = CREATED.plusSeconds(30);
        CatalogService changed = CatalogService.restore(original.serviceId(), tenant, CatalogServiceType.TOUR,
                "Updated item", CatalogStatus.INACTIVE, CommercialTerms.of(9L, 7L, 23, false, false),
                CREATED.plusSeconds(10), updated);
        repository.save(changed);
        CatalogService restored = repository.findByTenantAndId(tenant, original.serviceId()).orElseThrow();
        assertThat(restored.serviceId()).isEqualTo(original.serviceId());
        assertThat(restored.createdAt()).isEqualTo(micros(CREATED));
        assertThat(restored.updatedAt()).isEqualTo(micros(updated));
        assertThat(restored.commercialName()).isEqualTo("Updated item");
        assertThat(restored.catalogStatus()).isEqualTo(CatalogStatus.INACTIVE);
        assertThat(restored.terms()).isEqualTo(changed.terms());
    }

    @Test
    void anItemOfAnotherTenantIsNeverReadNorListedNorOverwritten() {
        CatalogService original = item(UUID.randomUUID(), tenant, CREATED);
        repository.save(original);
        UUID otherTenant = UUID.randomUUID();
        assertThat(repository.findByTenantAndId(otherTenant, original.serviceId())).isEmpty();
        assertThat(repository.listByTenant(otherTenant, Optional.empty())).isEmpty();
        CatalogService replacement = item(original.serviceId(), otherTenant, CREATED.plusSeconds(60));
        replacement.updateDescriptiveInformation("Attempted overwrite", CREATED.plusSeconds(60));
        assertThatThrownBy(() -> repository.save(replacement)).isInstanceOf(IllegalStateException.class);
        CatalogService restored = repository.findByTenantAndId(tenant, original.serviceId()).orElseThrow();
        assertThat(restored.tenantId()).isEqualTo(tenant);
        assertThat(restored.commercialName()).isEqualTo(original.commercialName());
        assertThat(restored.createdAt()).isEqualTo(micros(CREATED));
    }

    @Test
    void listingIsNewestFirstAndFiltersByStatus() {
        CatalogService oldest = item(UUID.randomUUID(), tenant, CREATED);
        // IDs are unique across repeated verify runs, while their database ordering remains explicit.
        UUID prefix = UUID.randomUUID();
        CatalogService lowerTie = item(new UUID(prefix.getMostSignificantBits(), 1), tenant, CREATED.plusSeconds(1));
        CatalogService upperTie = item(new UUID(prefix.getMostSignificantBits(), 2), tenant, CREATED.plusSeconds(1));
        upperTie.inactivate(actor, CREATED.plusSeconds(2));
        for (CatalogService service : List.of(oldest, lowerTie, upperTie)) {
            repository.save(service);
        }
        CatalogService other = item(UUID.randomUUID(), UUID.randomUUID(), CREATED.plusSeconds(3));
        repository.save(other);
        assertThat(repository.listByTenant(tenant, Optional.empty())).extracting(CatalogService::serviceId)
                .containsExactly(upperTie.serviceId(), lowerTie.serviceId(), oldest.serviceId());
        assertThat(repository.listByTenant(tenant, Optional.of(CatalogStatus.ACTIVE)))
                .extracting(CatalogService::serviceId).containsExactly(lowerTie.serviceId(), oldest.serviceId());
        assertThat(repository.listByTenant(tenant, Optional.of(CatalogStatus.INACTIVE)))
                .extracting(CatalogService::serviceId).containsExactly(upperTie.serviceId());
        assertThat(repository.listByTenant(other.tenantId(), Optional.empty()))
                .extracting(CatalogService::serviceId).containsExactly(other.serviceId());
    }

    @Test
    void inactivationAndReactivationWriteTheirOutboxRowInTheSameTransaction() {
        CatalogService service = item(UUID.randomUUID(), tenant, CREATED);
        repository.save(service);
        assertThat(events(service.serviceId())).isEmpty();
        Instant inactivatedAt = CREATED.plusSeconds(1);
        service.inactivate(actor, inactivatedAt);
        transactions.executeWithoutResult(status -> {
            repository.save(service);
            assertThat(events(service.serviceId())).containsExactly(new StoredEvent(tenant,
                    "CatalogServiceInactivated", service.serviceId(), "ACTIVE", "INACTIVE", actor,
                    micros(inactivatedAt), null));
        });
        service.inactivate(actor, CREATED.plusSeconds(2));
        repository.save(service);
        assertThat(events(service.serviceId())).hasSize(1);
        Instant reactivatedAt = CREATED.plusSeconds(3);
        service.reactivate(actor, reactivatedAt);
        repository.save(service);
        assertThat(events(service.serviceId())).containsExactly(
                new StoredEvent(tenant, "CatalogServiceInactivated", service.serviceId(), "ACTIVE", "INACTIVE",
                        actor, micros(inactivatedAt), null),
                new StoredEvent(tenant, "CatalogServiceReactivated", service.serviceId(), "INACTIVE", "ACTIVE",
                        actor, micros(reactivatedAt), null));
        service.reactivate(actor, CREATED.plusSeconds(4));
        repository.save(service);
        assertThat(events(service.serviceId())).hasSize(2);
        assertThat(service.pullEvents()).isEmpty();
    }

    @Test
    void aRolledBackTransactionLeavesNeitherTheItemNorItsEvent() {
        CatalogService service = item(UUID.randomUUID(), tenant, CREATED);
        service.inactivate(actor, CREATED.plusSeconds(1));
        transactions.executeWithoutResult(status -> {
            repository.save(service);
            assertThat(repository.findByTenantAndId(tenant, service.serviceId())).isPresent();
            assertThat(events(service.serviceId())).hasSize(1);
            status.setRollbackOnly();
        });
        assertThat(repository.findByTenantAndId(tenant, service.serviceId())).isEmpty();
        assertThat(events(service.serviceId())).isEmpty();
    }

    @Test
    void sameKeyInTheSameTenantRegistersOnce() {
        UUID original = UUID.randomUUID();
        assertThat(findKey(tenant, "same-key")).isEmpty();
        assertThat(registerKey(tenant, "same-key", original)).isTrue();
        assertThat(registerKey(tenant, "same-key", UUID.randomUUID())).isFalse();
        assertThat(findKey(tenant, "same-key")).contains(original);
    }

    @Test
    void sameKeyInAnotherTenantRegistersToo() {
        UUID otherTenant = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(registerKey(tenant, "shared-key", first)).isTrue();
        assertThat(findKey(otherTenant, "shared-key")).isEmpty();
        assertThat(registerKey(otherTenant, "shared-key", second)).isTrue();
        assertThat(findKey(tenant, "shared-key")).contains(first);
        assertThat(findKey(otherTenant, "shared-key")).contains(second);
    }

    @Test
    void aDuplicateKeyRollsBackTheSecondResource() {
        CatalogService original = item(UUID.randomUUID(), tenant, CREATED);
        transactions.executeWithoutResult(status -> {
            repository.save(original);
            assertThat(registerKey(tenant, "creation-key", original.serviceId())).isTrue();
        });
        CatalogService duplicate = item(UUID.randomUUID(), tenant, CREATED.plusSeconds(1));
        transactions.executeWithoutResult(status -> {
            repository.save(duplicate);
            assertThat(registerKey(tenant, "creation-key", duplicate.serviceId())).isFalse();
            status.setRollbackOnly();
        });
        assertThat(repository.findByTenantAndId(tenant, duplicate.serviceId())).isEmpty();
        assertThat(repository.findByTenantAndId(tenant, original.serviceId())).isPresent();
        assertThat(findKey(tenant, "creation-key")).contains(original.serviceId());
    }

    @Test
    void theApplicationUserNeedsNoDelete() {
        CatalogService service = item(UUID.randomUUID(), tenant, CREATED);
        repository.save(service);
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, ?)", Boolean.class,
                "commercial_reservation.catalog_service", "DELETE")).isFalse();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM commercial_reservation.catalog_service WHERE service_id = ?",
                service.serviceId())).isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        assertThat(repository.findByTenantAndId(tenant, service.serviceId())).isPresent();
    }

    private CatalogService item(UUID id, UUID owner, Instant created) {
        return CatalogService.register(id, owner, CatalogServiceType.TOUR, "Generated item",
                CommercialTerms.of(100_000L, 60_000L, 12, true, false), created);
    }

    private List<StoredEvent> events(UUID serviceId) {
        return jdbc.query("SELECT tenant_id, event_type, service_id, previous_catalog_status, new_catalog_status, "
                + "actor_id, occurred_at, published_at FROM commercial_reservation.outbox_event "
                + "WHERE tenant_id = ? AND service_id = ? ORDER BY occurred_at", (row, index) -> new StoredEvent(
                        row.getObject("tenant_id", UUID.class), row.getString("event_type"),
                        row.getObject("service_id", UUID.class), row.getString("previous_catalog_status"),
                        row.getString("new_catalog_status"), row.getObject("actor_id", UUID.class),
                        instant(row, "occurred_at"), instant(row, "published_at")), tenant, serviceId);
    }

    private boolean registerKey(UUID owner, String key, UUID resourceId) {
        return keys.tryRegister(owner, key, "CatalogService", resourceId);
    }

    private Optional<UUID> findKey(UUID owner, String key) {
        return keys.findResourceId(owner, key);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }

    private record StoredEvent(UUID tenantId, String eventType, UUID serviceId, String previousStatus,
            String newStatus, UUID actorId, Instant occurredAt, Instant publishedAt) { }
}
