package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEstablishmentImages;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EstablishmentImages;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAssociatedEstablishmentUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
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
class EstablishmentPersistenceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private AssociatedEstablishmentRepository repository;
    private AssociatedEstablishmentKeys keys;
    private EstablishmentImages images;
    private RegisterAssociatedEstablishment register;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER")
                && present("TEST_DATABASE_PASSWORD"), "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var jdbcKeys = new JdbcIdempotencyKeys(jdbc);
        repository = new JdbcAssociatedEstablishmentRepository(jdbc, transactions);
        keys = new JdbcAssociatedEstablishmentKeys(jdbc, jdbcKeys, repository, transactions);
        images = new JdbcEstablishmentImages(jdbc, transactions);
        register = new RegisterAssociatedEstablishmentUseCase(repository, keys, UUID::randomUUID,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("commercial_reservation_app");
    }

    @Test
    void registrationCommitsImageAssociationIdempotencyKeyAndOutboxTogether() {
        var actor = actor(UUID.randomUUID());
        var result = persist(actor, "establishment-key-one", PNG, "image/png");
        UUID associationId = result.association().associationId();

        assertThat(result.created()).isTrue();
        assertThat(count("associated_establishment", "association_id", associationId)).isEqualTo(1);
        assertThat(count("associated_establishment_image", "association_id", associationId)).isEqualTo(1);
        assertThat(count("idempotency_key", "resource_id", associationId)).isEqualTo(1);
        assertThat(jdbc.query("SELECT event_type, association_id, service_id, previous_catalog_status, "
                + "new_catalog_status, actor_id FROM commercial_reservation.outbox_event "
                + "WHERE association_id = ?", (row, index) -> row.getString("event_type") + ":"
                + row.getObject("association_id", UUID.class) + ":" + row.getObject("service_id", UUID.class)
                + ":" + row.getString("previous_catalog_status") + ":" + row.getString("new_catalog_status")
                + ":" + row.getObject("actor_id", UUID.class), associationId))
                .containsExactly("EstablishmentAssociationPublished:" + associationId + ":null:null:ACTIVE:"
                        + actor.actorId());
        var storedImage = jdbc.queryForMap("SELECT content, content_type, size_bytes, sha256 "
                + "FROM commercial_reservation.associated_establishment_image WHERE association_id = ?", associationId);
        assertThat((byte[]) storedImage.get("content")).isEqualTo(PNG);
        assertThat(storedImage.get("content_type")).isEqualTo("image/png");
        assertThat(storedImage.get("size_bytes")).isEqualTo(PNG.length);
        assertThat(storedImage.get("sha256")).isEqualTo(sha256(PNG));
    }

    @Test
    void anOutboxConstraintFailureRollsBackEveryRegistrationWrite() {
        var actor = actor(UUID.randomUUID());
        var command = command("Rollback hotel", UUID.randomUUID().toString());
        assertThatThrownBy(() -> images.register(PNG, "image/png", () -> {
            var result = register.register(actor, "establishment-key-rollback", command);
            jdbc.update("INSERT INTO commercial_reservation.outbox_event "
                    + "(tenant_id, event_type, association_id, service_id, previous_catalog_status, "
                    + "new_catalog_status, actor_id, occurred_at) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?)",
                    actor.tenantId(), "InvalidEstablishmentEvent", result.association().associationId(),
                    "ACTIVE", actor.actorId(), OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
            return result;
        })).isInstanceOf(DataAccessException.class);

        assertThat(count("associated_establishment", "tenant_id", actor.tenantId())).isZero();
        assertThat(count("associated_establishment_image", "tenant_id", actor.tenantId())).isZero();
        assertThat(count("idempotency_key", "tenant_id", actor.tenantId())).isZero();
        assertThat(count("outbox_event", "tenant_id", actor.tenantId())).isZero();
    }

    @Test
    void replayWithTheSameKeyReturnsTheAssociationWithoutWritingAnotherImage() {
        var actor = actor(UUID.randomUUID());
        var first = persist(actor, "establishment-key-replay", PNG, "image/png");
        var retry = persist(actor, "establishment-key-replay", PNG, "image/png");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.association().associationId()).isEqualTo(first.association().associationId());
        assertThat(count("associated_establishment", "tenant_id", actor.tenantId())).isEqualTo(1);
        assertThat(count("associated_establishment_image", "tenant_id", actor.tenantId())).isEqualTo(1);
        assertThat(count("idempotency_key", "tenant_id", actor.tenantId())).isEqualTo(1);
        assertThat(count("outbox_event", "tenant_id", actor.tenantId())).isEqualTo(1);
    }

    @Test
    void repositoryReadsAndListsOnlyWithinTheRequestedTenant() {
        var owner = actor(UUID.randomUUID());
        var item = persist(owner, "establishment-key-tenant", PNG, "image/png").association();
        var otherTenant = UUID.randomUUID();

        assertThat(repository.findByTenantAndId(otherTenant, item.associationId())).isEmpty();
        assertThat(repository.listByTenant(otherTenant, java.util.Optional.empty(), java.util.Optional.empty(), 1, 20)
                .total()).isZero();
        assertThat(repository.findByTenantAndId(owner.tenantId(), item.associationId()))
                .map(AssociatedEstablishment::associationId).contains(item.associationId());
    }

    @Test
    void publicImageReadIsScopedToItsTenant() {
        var owner = actor(UUID.randomUUID());
        var item = persist(owner, "establishment-key-image-read", PNG, "image/png").association();

        assertThat(images.read(owner.tenantId(), item.imageReference()).bytes()).isEqualTo(PNG);
        assertThatThrownBy(() -> images.read(UUID.randomUUID(), item.imageReference()))
                .isInstanceOf(AssociatedEstablishmentNotFoundException.class);
    }

    @Test
    void databaseImageChecksRejectUnsupportedTypesAndOversizedContentAtomically() {
        for (var invalid : new Object[][] { {PNG, "image/svg+xml"}, {new byte[819201], "image/png"} }) {
            var actor = actor(UUID.randomUUID());
            byte[] bytes = (byte[]) invalid[0];
            String contentType = (String) invalid[1];
            assertThatThrownBy(() -> persist(actor, "establishment-key-check-" + UUID.randomUUID(), bytes, contentType))
                    .isInstanceOf(DataAccessException.class);
            assertThat(count("associated_establishment", "tenant_id", actor.tenantId())).isZero();
            assertThat(count("associated_establishment_image", "tenant_id", actor.tenantId())).isZero();
            assertThat(count("idempotency_key", "tenant_id", actor.tenantId())).isZero();
            assertThat(count("outbox_event", "tenant_id", actor.tenantId())).isZero();
        }
    }

    private co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration persist(
            EstablishmentActor actor, String key, byte[] bytes, String mediaType) {
        return images.register(bytes, mediaType, () -> register.register(actor, key,
                command("Generated hotel", UUID.randomUUID().toString())));
    }

    private EstablishmentActor actor(UUID tenant) {
        return new EstablishmentActor(tenant, UUID.randomUUID(), EstablishmentActor.Role.TENANT_ADMINISTRATOR);
    }

    private RegisterAssociatedEstablishment.Command command(String name, String imageReference) {
        return new RegisterAssociatedEstablishment.Command("HOTEL", name, "Generated contact", imageReference);
    }

    private int count(String table, String column, UUID value) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation." + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }
}
