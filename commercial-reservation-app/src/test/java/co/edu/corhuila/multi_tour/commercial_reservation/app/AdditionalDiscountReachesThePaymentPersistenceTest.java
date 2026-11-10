package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RegisterAdditionalDiscountUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-004 AC4 and AC-04.4 (api spec 004 DP-01): the additional discount is registered on the reservation before the charge;
// the charge (HU-COM-009) reads the value left and never applies a second discount. Over the real schema. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class AdditionalDiscountReachesThePaymentPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final long BEFORE = 324_000, DISCOUNT = 32_400, AFTER = 291_600; // 10 percent of the subtotal left by the fixed lines
    private final UUID reservation = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcReservationRepository reservations;
    private RegisterAdditionalDiscountUseCase register;
    private ReservationPersistenceFixture fixture;
    private UUID reasonId;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(jdbc, fixture.cipher, transactions, new JdbcReservationCreatedOutbox(jdbc));
        reservations.save(fixture.reservation(reservation, true));
        var reasons = new JdbcAuthorizableReasonRepository(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        var reason = AuthorizableReason.create(fixture.tenant, UUID.randomUUID(), "Customer loyalty");
        reasons.save(reason);
        reasonId = reason.reasonId();
        DiscountRuleTransaction transaction = new DiscountRuleTransaction() {
            @Override public <T> T inTransaction(Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }
        };
        register = new RegisterAdditionalDiscountUseCase(new JdbcReservationReader(jdbc, fixture.cipher),
                new JdbcAdditionalDiscountRepository(jdbc), reasons, new JdbcReservationModifiedOutbox(jdbc, new ObjectMapper()),
                (tenantId, id, excess) -> { }, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void registerTenPercent() {
        register.register(new DiscountRuleActor(fixture.tenant, fixture.actor, DiscountRuleActor.Role.TENANT_ADMINISTRATOR),
                new RegisterAdditionalDiscount.Command(reservation, reasonId, 1_000));
    }

    @Test
    void theRegisteredDiscountLowersTheValueAndTheBalanceTheChargeReads() {
        assertThat(reservations.findByTenantAndId(fixture.tenant, reservation).orElseThrow().pendingBalance()).isEqualTo(new Cents(BEFORE));

        registerTenPercent();

        var charged = reservations.findByTenantAndId(fixture.tenant, reservation).orElseThrow();
        assertThat(charged.finalValue()).isEqualTo(new Cents(AFTER));
        assertThat(charged.pendingBalance()).isEqualTo(new Cents(AFTER));
    }

    @Test
    void thePaymentChargesTheValueWithTheDiscountAndAppliesNoSecondOne() {
        registerTenPercent();
        var charged = reservations.findByTenantAndId(fixture.tenant, reservation).orElseThrow();

        var attempt = charged.registerTransfer(UUID.randomUUID(), new Cents(AFTER), new Cents(100_000), fixture.actor, NOW);

        assertThat(attempt.amount()).isEqualTo(new Cents(AFTER));
        // What was due before the discount can no longer be charged, and the payment wrote no discount of its own.
        assertThatThrownBy(() -> charged.registerTransfer(UUID.randomUUID(), new Cents(BEFORE), new Cents(100_000), fixture.actor, NOW))
                .isInstanceOf(DomainException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation_additional_discount "
                + "WHERE reservation_id = ?", Integer.class, reservation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM commercial_reservation.reservation_additional_discount "
                + "WHERE reservation_id = ?", Long.class, reservation)).isEqualTo(DISCOUNT);
    }
}
