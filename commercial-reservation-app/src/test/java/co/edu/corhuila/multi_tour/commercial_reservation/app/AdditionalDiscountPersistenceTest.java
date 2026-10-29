package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAdditionalDiscountUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class AdditionalDiscountPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private AdditionalDiscountRepository discounts;
    private JdbcAuthorizableReasonRepository reasons;
    private ReservationModifiedOutbox outbox;
    private final UUID tenant = UUID.randomUUID();
    private final UUID reservation = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        discounts = new JdbcAdditionalDiscountRepository(jdbc);
        outbox = new JdbcReservationModifiedOutbox(jdbc, JSON);
        reasons = new JdbcAuthorizableReasonRepository(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
    }

    @Test
    void theDiscountIsStoredWithItsRateReasonAuthorAndValues() {
        discounts.save(discount(tenant, reservation, 1_000, 200_000));

        var row = jdbc.queryForMap("SELECT * FROM commercial_reservation.reservation_additional_discount "
                + "WHERE reservation_id = ?", reservation);
        assertThat(row.get("tenant_id")).isEqualTo(tenant);
        assertThat(row.get("discount_percentage_bp")).isEqualTo(1_000);
        assertThat(row.get("previous_value_cents")).isEqualTo(200_000L);
        assertThat(row.get("amount_cents")).isEqualTo(20_000L);
        assertThat(row.get("reason")).isEqualTo("Customer loyalty");
        assertThat(row.get("applied_by")).isEqualTo(actor);
    }

    @Test
    void existenceIsAnsweredPerTenant() {
        discounts.save(discount(tenant, reservation, 500, 100_000));

        assertThat(discounts.existsByReservation(tenant, reservation)).isTrue();
        assertThat(discounts.existsByReservation(UUID.randomUUID(), reservation)).isFalse();
        assertThat(discounts.existsByReservation(tenant, UUID.randomUUID())).isFalse();
    }

    @Test
    void aSecondDiscountOnTheSameReservationIsABusinessRuleViolation() {
        discounts.save(discount(tenant, reservation, 500, 100_000));

        assertThatThrownBy(() -> discounts.save(discount(tenant, reservation, 700, 100_000)))
                .isInstanceOf(DomainException.class).hasMessageContaining("already has an additional discount");
    }

    @Test
    void theApplicationUserCannotUpdateTheRecord() {
        discounts.save(discount(tenant, reservation, 500, 100_000));

        assertThatThrownBy(() -> jdbc.update("UPDATE commercial_reservation.reservation_additional_discount "
                + "SET reason = 'edited' WHERE reservation_id = ?", reservation)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void registrationCommitsTheDiscountAndItsReservationModifiedEventTogether() {
        UUID reasonId = enabledReason();
        var result = useCase(outbox).register(admin(), new RegisterAdditionalDiscount.Command(reservation, reasonId, 1_000));

        assertThat(discounts.existsByReservation(tenant, reservation)).isTrue();
        var event = jdbc.queryForMap("SELECT * FROM commercial_reservation.outbox_event WHERE reservation_id = ?",
                reservation);
        assertThat(event.get("event_type")).isEqualTo("ReservationModified");
        assertThat(event.get("tenant_id")).isEqualTo(tenant);
        assertThat(event.get("actor_id")).isEqualTo(actor);
        assertThat(event.get("service_id")).isNull();
        assertThat(event.get("previous_catalog_status")).isNull();
        assertThat(event.get("published_at")).isNull();
        JsonNode payload = json(event.get("payload"));
        assertThat(payload.get("previousSubtotalCents").asLong()).isEqualTo(200_000L);
        assertThat(payload.get("newSubtotalCents").asLong()).isEqualTo(180_000L);
        assertThat(payload.get("reason").asText()).isEqualTo(result.reason());
        assertThat(payload.get("authorizerId").asText()).isEqualTo(actor.toString());
        assertThat(payload.get("order").asInt()).isEqualTo(1);
    }

    @Test
    void aFailingEventRollsTheDiscountBack() {
        UUID reasonId = enabledReason();
        ReservationModifiedOutbox failing = event -> {
            outbox.append(event);
            throw new IllegalStateException("broker down");
        };

        assertThatThrownBy(() -> useCase(failing).register(admin(),
                new RegisterAdditionalDiscount.Command(reservation, reasonId, 1_000)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(discounts.existsByReservation(tenant, reservation)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event "
                + "WHERE reservation_id = ?", Integer.class, reservation)).isZero();
    }

    private RegisterAdditionalDiscountUseCase useCase(ReservationModifiedOutbox events) {
        var snapshot = new ReservationSnapshot(tenant, reservation, ReservationSnapshot.State.PENDING_PAYMENT,
                new Cents(200_000), new Cents(0), 0);
        DiscountRuleTransaction transaction = new DiscountRuleTransaction() {
            @Override public <T> T inTransaction(Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }
        };
        return new RegisterAdditionalDiscountUseCase(
                (tenantId, id) -> tenantId.equals(tenant) ? Optional.of(snapshot) : Optional.empty(), discounts,
                reasons, events, (tenantId, id, excess) -> { }, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UUID enabledReason() {
        var reason = AuthorizableReason.create(tenant, UUID.randomUUID(), "Customer loyalty");
        reasons.save(reason);
        return reason.reasonId();
    }

    private DiscountRuleActor admin() {
        return new DiscountRuleActor(tenant, actor, DiscountRuleActor.Role.TENANT_ADMINISTRATOR);
    }

    private AdditionalDiscount discount(UUID tenantId, UUID reservationId, int basisPoints, long base) {
        var rate = new BasisPoints(basisPoints);
        return new AdditionalDiscount(tenantId, reservationId, rate, DiscountLine.percentage(new Cents(base), rate),
                "Customer loyalty", actor, 1, NOW);
    }

    private static JsonNode json(Object value) {
        try {
            return JSON.readTree(value.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
