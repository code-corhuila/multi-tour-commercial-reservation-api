package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEstablishmentImages;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcIdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAssociatedEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateAssociatedEstablishmentUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReactivateAssociatedEstablishmentUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAssociatedEstablishmentUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** RN-11: each status transition writes its outbox event in the same transaction as the status change. */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class EstablishmentTransitionOutboxTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant INACTIVATED_AT = Instant.parse("2026-10-12T08:30:00Z");
    private static final Instant REACTIVATED_AT = Instant.parse("2026-10-13T09:45:00Z");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcEstablishmentImages images;
    private RegisterAssociatedEstablishment register;
    private InactivateAssociatedEstablishment inactivate;
    private ReactivateAssociatedEstablishment reactivate;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER")
                && present("TEST_DATABASE_PASSWORD"), "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var repository = new JdbcAssociatedEstablishmentRepository(jdbc, transactions);
        var keys = new JdbcAssociatedEstablishmentKeys(jdbc, new JdbcIdempotencyKeys(jdbc), repository, transactions);
        images = new JdbcEstablishmentImages(jdbc, transactions);
        register = new RegisterAssociatedEstablishmentUseCase(repository, keys, UUID::randomUUID,
                Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));
        inactivate = new InactivateAssociatedEstablishmentUseCase(repository,
                Clock.fixed(INACTIVATED_AT, ZoneOffset.UTC));
        reactivate = new ReactivateAssociatedEstablishmentUseCase(repository,
                Clock.fixed(REACTIVATED_AT, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
    }

    @Test
    void inactivationWritesItsOutboxRowWithTheStatusChange() {
        var actor = actor();
        UUID id = registered(actor);

        inactivate.inactivate(actor, id);

        assertThat(status(actor, id)).isEqualTo("INACTIVE");
        assertThat(transitionRows(actor, id, "EstablishmentAssociationInactivated")).singleElement()
                .satisfies(row -> assertRow(row, actor, id, "INACTIVE", INACTIVATED_AT));
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished",
                "EstablishmentAssociationInactivated");
    }

    @Test
    void reactivationWritesItsOutboxRowWithTheStatusChange() {
        var actor = actor();
        UUID id = registered(actor);
        inactivate.inactivate(actor, id);

        reactivate.reactivate(actor, id);

        assertThat(status(actor, id)).isEqualTo("ACTIVE");
        assertThat(transitionRows(actor, id, "EstablishmentAssociationReactivated")).singleElement()
                .satisfies(row -> assertRow(row, actor, id, "ACTIVE", REACTIVATED_AT));
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished",
                "EstablishmentAssociationInactivated", "EstablishmentAssociationReactivated");
    }

    @Test
    void repeatingTheSameTransitionChangesNothingAndWritesNoSecondEvent() {
        var actor = actor();
        UUID id = registered(actor);
        inactivate.inactivate(actor, id);
        inactivate.inactivate(actor, id);
        assertThat(status(actor, id)).isEqualTo("INACTIVE");
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished",
                "EstablishmentAssociationInactivated");

        reactivate.reactivate(actor, id);
        reactivate.reactivate(actor, id);
        assertThat(status(actor, id)).isEqualTo("ACTIVE");
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished",
                "EstablishmentAssociationInactivated", "EstablishmentAssociationReactivated");
    }

    @Test
    void aFailureAfterInactivatingLeavesNeitherTheStatusChangeNorTheEvent() {
        var actor = actor();
        UUID id = registered(actor);

        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            inactivate.inactivate(actor, id);
            throw new IllegalStateException("forced failure after save");
        })).isInstanceOf(IllegalStateException.class).hasMessage("forced failure after save");

        assertThat(status(actor, id)).isEqualTo(AssociationStatus.ACTIVE.name());
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished");
    }

    @Test
    void aFailureAfterReactivatingLeavesNeitherTheStatusChangeNorTheEvent() {
        var actor = actor();
        UUID id = registered(actor);
        inactivate.inactivate(actor, id);

        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            reactivate.reactivate(actor, id);
            throw new IllegalStateException("forced failure after save");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(status(actor, id)).isEqualTo(AssociationStatus.INACTIVE.name());
        assertThat(eventTypes(actor, id)).containsExactly("EstablishmentAssociationPublished",
                "EstablishmentAssociationInactivated");
    }

    private void assertRow(Map<String, Object> row, EstablishmentActor actor, UUID id, String next, Instant at) {
        assertThat(row.get("tenant_id")).isEqualTo(actor.tenantId());
        assertThat(row.get("association_id")).isEqualTo(id);
        assertThat(row.get("actor_id")).isEqualTo(actor.actorId());
        // Establishment events carry no previous status (no catalog service involved); the new status is explicit.
        assertThat(row.get("previous_catalog_status")).isNull();
        assertThat(row.get("new_catalog_status")).isEqualTo(next);
        assertThat(row.get("service_id")).isNull();
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(at);
    }

    private UUID registered(EstablishmentActor actor) {
        var command = new RegisterAssociatedEstablishment.Command("HOTEL", "Transition hotel", "Generated contact",
                UUID.randomUUID().toString());
        return images.register(PNG, "image/png", () -> register.register(actor, "key-" + UUID.randomUUID(), command))
                .association().associationId();
    }

    private String status(EstablishmentActor actor, UUID id) {
        return jdbc.queryForObject("SELECT association_status FROM commercial_reservation.associated_establishment "
                + "WHERE tenant_id = ? AND association_id = ?", String.class, actor.tenantId(), id);
    }

    private List<Map<String, Object>> transitionRows(EstablishmentActor actor, UUID id, String type) {
        return jdbc.queryForList("SELECT tenant_id, association_id, service_id, previous_catalog_status, "
                + "new_catalog_status, actor_id, occurred_at FROM commercial_reservation.outbox_event "
                + "WHERE tenant_id = ? AND association_id = ? AND event_type = ?", actor.tenantId(), id, type);
    }

    private List<String> eventTypes(EstablishmentActor actor, UUID id) {
        return jdbc.queryForList("SELECT event_type FROM commercial_reservation.outbox_event "
                + "WHERE tenant_id = ? AND association_id = ? ORDER BY occurred_at", String.class,
                actor.tenantId(), id);
    }

    private EstablishmentActor actor() {
        return new EstablishmentActor(UUID.randomUUID(), UUID.randomUUID(),
                EstablishmentActor.Role.TENANT_ADMINISTRATOR);
    }

    private static boolean present(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }
}
