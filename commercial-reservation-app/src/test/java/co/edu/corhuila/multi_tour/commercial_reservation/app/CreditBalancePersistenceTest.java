package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreditBalanceRecording;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RecordCreditBalanceUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication.AppliedBy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-017 over the real schema (api tasks T-02, T-03, T-05; AC-17.1, AC-17.3, AC-17.7, AC-17.9, AC-17.14): the credit balance and its applications are stored
// in the tables and guards of the db, the available value is derived from the applications, a repeated excess records nothing more, and another tenant sees
// nothing. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CreditBalancePersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcCreditBalanceRepository credits;
    private JdbcFinancialDecisionRepository decisions;
    private RecordCreditBalanceUseCase record;
    private FinancialDecisionOpening opening;
    private JdbcReservationRepository reservations;
    private PaymentActor admin;
    private UUID reservationId;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        credits = new JdbcCreditBalanceRepository(fixture.jdbc);
        decisions = new JdbcFinancialDecisionRepository(fixture.jdbc);
        var ids = new UuidGenerator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        record = new RecordCreditBalanceUseCase(decisions, credits, ids, new JdbcPaymentEventOutbox(fixture.jdbc),
                new JdbcPaymentTransaction(fixture.transactions), clock);
        opening = new FinancialDecisionOpening(decisions, new JdbcRefundAuthorizationRepository(fixture.jdbc), new WeekdayBusinessCalendar(),
                new JdbcRefundAuthorizedOutbox(fixture.jdbc), ids, clock);
        admin = new PaymentActor(fixture.tenant, fixture.actor, Role.TENANT_ADMINISTRATOR);
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservationId = UUID.randomUUID();
        reservations.save(fixture.reservation(reservationId, true));
    }

    private CreditBalance creditOfADecision() {
        opening.open(fixture.tenant, reservationId, new Cents(80_000), new Cents(0), NOW);
        UUID decision = decisions.findLatest(fixture.tenant, reservationId, DecisionKind.CUSTOMER_CANCELLATION).orElseThrow().id();
        return record.recordCreditDecision(admin, decision);
    }

    private CreditApplication application(CreditBalance credit, UUID target, long cents) {
        return new CreditApplication(UUID.randomUUID(), credit.id(), fixture.tenant, target, new Cents(cents), NOW, fixture.actor, AppliedBy.STAFF);
    }

    private UUID anotherReservationOfTheSameCustomer() {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, false));
        return id;
    }

    @Test
    void theCreditOfADecisionIsStoredOnceAndFoundByItsDecisionAndIsolatedByTenant() {
        CreditBalance credit = creditOfADecision();

        CreditBalance stored = credits.find(fixture.tenant, credit.id()).orElseThrow();
        assertThat(stored).isEqualTo(credit);
        assertThat(stored.origin()).isEqualTo(CreditOrigin.CUSTOMER_CANCELLATION);
        assertThat(stored.available()).isEqualTo(new Cents(80_000));
        assertThat(credits.findByDecision(fixture.tenant, credit.sourceDecisionId())).contains(credit);
        assertThat(credits.find(UUID.randomUUID(), credit.id())).isEmpty();
        credits.save(credit);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.credit_balance WHERE tenant_id = ?", Integer.class,
                fixture.tenant)).isEqualTo(1);
    }

    @Test
    void theAppliedValueIsDerivedFromTheApplicationsAndTheEngineRefusesAnExcess() {
        CreditBalance credit = creditOfADecision();
        UUID target = anotherReservationOfTheSameCustomer();

        credits.save(application(credit, target, 30_000));

        CreditBalance stored = credits.find(fixture.tenant, credit.id()).orElseThrow();
        assertThat(stored.applied()).isEqualTo(new Cents(30_000));
        assertThat(stored.available()).isEqualTo(new Cents(50_000));
        assertThatThrownBy(() -> credits.save(application(credit, target, 50_001)))
                .hasMessageContaining("trg_credit_balance_application_guard");
    }

    @Test
    void anExcessIsOneCreditPerAttemptAndARepeatRecordsNothingMore() {
        UUID attempt = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_attempt (payment_attempt_id, tenant_id, reservation_id, payment_method, "
                + "received_amount_cents, validation_status, payment_status_snapshot, registered_by, registered_at) "
                + "VALUES (?, ?, ?, 'TRANSFER', 100000, 'IN_VALIDATION', 'NO_PAYMENT', ?, now())", attempt, fixture.tenant, reservationId, fixture.actor);
        var recording = new CreditBalanceRecording(credits, new UuidGenerator(), new JdbcPaymentEventOutbox(fixture.jdbc), Clock.fixed(NOW, ZoneOffset.UTC));

        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));
        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));

        CreditBalance stored = credits.findByAttempt(fixture.tenant, attempt).orElseThrow();
        assertThat(stored.origin()).isEqualTo(CreditOrigin.PAYMENT_EXCESS);
        assertThat(stored.available()).isEqualTo(new Cents(12_000));
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.credit_balance WHERE source_payment_attempt_id = ?",
                Integer.class, attempt)).isEqualTo(1);
    }

    // T-06 / AC-17.17, AC-17.18: the credit events are written to the outbox with the closed payload of the db (identifiers, amount, origin), never a name or a reason.
    @Test
    void theRecordingWritesOneEventWithTheClosedPayloadAndARepeatOfTheExcessWritesNone() {
        CreditBalance credit = creditOfADecision();
        UUID attempt = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_attempt (payment_attempt_id, tenant_id, reservation_id, payment_method, "
                + "received_amount_cents, validation_status, payment_status_snapshot, registered_by, registered_at) "
                + "VALUES (?, ?, ?, 'TRANSFER', 100000, 'IN_VALIDATION', 'NO_PAYMENT', ?, now())", attempt, fixture.tenant, reservationId, fixture.actor);
        var recording = new CreditBalanceRecording(credits, new UuidGenerator(), new JdbcPaymentEventOutbox(fixture.jdbc), Clock.fixed(NOW, ZoneOffset.UTC));

        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));
        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));

        var payloads = fixture.jdbc.queryForList("SELECT payload::text FROM commercial_reservation.outbox_event WHERE tenant_id = ? AND reservation_id = ? "
                + "AND event_type = 'CreditBalanceRecorded' ORDER BY occurred_at, outbox_event_id", String.class, fixture.tenant, reservationId);
        assertThat(payloads).hasSize(2);
        assertThat(payloads).anySatisfy(payload -> assertThat(payload).contains(credit.id().toString()).contains("80000").contains("CUSTOMER_CANCELLATION"));
        assertThat(payloads).anySatisfy(payload -> assertThat(payload).contains("12000").contains("PAYMENT_EXCESS"));
        assertThat(payloads).allSatisfy(payload -> assertThat(payload).contains("customerId").doesNotContain("reason"));
    }
}
