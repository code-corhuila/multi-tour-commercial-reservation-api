package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-009 over the real schema (api tasks T-05, T-06): the payment parameters with the last change only, the minimum deposit
// computed on the final value and rounded up, and the open deadline of a reservation. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentConditionPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcPaymentParameterRepository parameters;
    private JdbcPaymentConditionReader conditions;
    private JdbcPaymentDeadlineReader deadlines;
    private JdbcReservationRepository reservations;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        parameters = new JdbcPaymentParameterRepository(fixture.jdbc);
        conditions = new JdbcPaymentConditionReader(fixture.jdbc);
        deadlines = new JdbcPaymentDeadlineReader(fixture.jdbc);
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
    }

    private PaymentParameter deposit(UUID service, int bp, String reason) {
        return new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, service, 24, 48, bp, 72, true, false), fixture.actor, reason,
                ReservationPersistenceFixture.NOW);
    }

    private Reservation reservation() {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        return reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    @Test
    void aRowIsReplacedByTheLastChangeAndTheTenantLevelAndTheServiceLevelAreApart() {
        parameters.save(fixture.tenant, deposit(null, 3_000, "First season"));
        parameters.save(fixture.tenant, deposit(null, 4_000, "Second season"));
        parameters.save(fixture.tenant, deposit(fixture.service, 2_000, "Only this tour"));

        var rows = parameters.findByTenant(fixture.tenant);

        assertThat(rows).hasSize(2);
        var tenantLevel = rows.stream().filter(row -> row.values().serviceId() == null).findFirst().orElseThrow();
        assertThat(tenantLevel.values().minimumDepositBp()).isEqualTo(4_000);
        assertThat(tenantLevel.values().balanceDeadlineAdvanceHours()).isEqualTo(72);
        assertThat(tenantLevel.values().transferEnabled()).isTrue();
        assertThat(tenantLevel.values().cashEnabled()).isFalse();
        assertThat(tenantLevel.changeReason()).isEqualTo("Second season");
        assertThat(tenantLevel.changedBy()).isEqualTo(fixture.actor);
        assertThat(parameters.findByTenant(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theMinimumDepositIsOnTheFinalValueRoundedUpAndTheServiceRowWinsOverTheTenantRow() {
        Reservation reservation = reservation();
        assertThat(conditions.minimumDepositFor(fixture.tenant, reservation)).isEmpty();

        parameters.save(fixture.tenant, deposit(null, 3_333, "Tenant level"));
        assertThat(conditions.minimumDepositFor(fixture.tenant, reservation)).contains(new Cents(107_990));

        parameters.save(fixture.tenant, deposit(fixture.service, 3_000, "This tour"));
        assertThat(conditions.minimumDepositFor(fixture.tenant, reservation)).contains(new Cents(97_200));
        assertThat(conditions.minimumDepositFor(UUID.randomUUID(), reservation)).isEmpty();
    }

    @Test
    void theOpenDeadlineIsTheOneOfTheReservationAndNothingElse() {
        Reservation reservation = reservation();
        assertThat(deadlines.openDeadline(fixture.tenant, reservation.reservationId())).isEmpty();
        OffsetDateTime due = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusHours(24);
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_deadline (payment_deadline_id, tenant_id, reservation_id, "
                + "deadline_kind, due_at) VALUES (?, ?, ?, 'INITIAL_PAYMENT', ?)", UUID.randomUUID(), fixture.tenant,
                reservation.reservationId(), due);

        var found = deadlines.openDeadline(fixture.tenant, reservation.reservationId()).orElseThrow();

        assertThat(found.kind()).isEqualTo(DeadlineKind.INITIAL_PAYMENT);
        assertThat(found.dueAt()).isEqualTo(due.toInstant());
        assertThat(deadlines.openDeadline(UUID.randomUUID(), reservation.reservationId())).isEmpty();
    }
}
