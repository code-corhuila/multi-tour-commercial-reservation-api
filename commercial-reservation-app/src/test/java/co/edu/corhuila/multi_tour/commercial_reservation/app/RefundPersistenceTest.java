package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageRefundDecisionsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RefundAutomationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder.Origin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-016 over the real schema (api tasks T-03, T-04, T-07; AC-16.2, AC-16.6, AC-16.8, AC-16.13, AC-16.15, AC-16.16, AC-16.18, AC-16.19): the use cases with
// the real adapters write the decision and the refund the guards of the db accept, the opening answers a late value, the outbox carries the six keys and no
// reason, the default writes an event with no author, and another tenant sees nothing. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RefundPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcFinancialDecisionRepository decisions;
    private JdbcRefundAuthorizationRepository refunds;
    private FinancialDecisionOpening opening;
    private ManageRefundDecisionsUseCase manage;
    private PaymentActor admin;
    private UUID reservationId;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        decisions = new JdbcFinancialDecisionRepository(fixture.jdbc);
        refunds = new JdbcRefundAuthorizationRepository(fixture.jdbc);
        var calendar = new WeekdayBusinessCalendar();
        var outbox = new JdbcRefundAuthorizedOutbox(fixture.jdbc);
        var ids = new UuidGenerator();
        var transaction = new JdbcPaymentTransaction(fixture.transactions);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        opening = new FinancialDecisionOpening(decisions, refunds, calendar, outbox, ids, clock);
        manage = new ManageRefundDecisionsUseCase(decisions, refunds, calendar, outbox, ids, transaction, clock);
        admin = new PaymentActor(fixture.tenant, fixture.actor, Role.TENANT_ADMINISTRATOR);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservationId = UUID.randomUUID();
        reservations.save(fixture.reservation(reservationId, true));
    }

    private FinancialDecision openCancellation() {
        opening.open(fixture.tenant, reservationId, new Cents(80_000), new Cents(20_000), NOW);
        return decisions.findLatest(fixture.tenant, reservationId, DecisionKind.CUSTOMER_CANCELLATION).orElseThrow();
    }

    private List<Map<String, Object>> events() {
        return fixture.jdbc.queryForList("SELECT actor_id, payload::text AS payload FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'RefundAuthorized' ORDER BY occurred_at", reservationId);
    }

    @Test
    void aRecognizedValueOpensOneDecisionAndALateValueUpdatesItsAmount() {
        FinancialDecision decision = openCancellation();

        assertThat(decision.baseAmount()).isEqualTo(new Cents(80_000));
        assertThat(decision.penalty()).isEqualTo(new Cents(20_000));
        assertThat(decision.recognizedAt()).isEqualTo(NOW);
        opening.record(fixture.tenant, reservationId, new Cents(5_000), Origin.PENDING_TREATMENT_AFTER_EXPIRY);
        opening.open(fixture.tenant, reservationId, new Cents(3_000), new Cents(0), NOW);
        assertThat(decisions.findLatest(fixture.tenant, reservationId, DecisionKind.CUSTOMER_CANCELLATION).orElseThrow().baseAmount())
                .isEqualTo(new Cents(83_000));
    }

    @Test
    void authorizingStoresTheRefundWithItsDueInstantOneEventAndNoReason() {
        FinancialDecision decision = openCancellation();
        manage.recordRefundDecision(admin, decision.id());

        RefundAuthorization refund = manage.authorize(admin, decision.id(), "Private note of the administrator");

        RefundAuthorization stored = refunds.find(fixture.tenant, refund.id()).orElseThrow();
        assertThat(stored).isEqualTo(refund);
        assertThat(stored.executionDueAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
        assertThat(events()).hasSize(1);
        Map<String, Object> event = events().get(0);
        assertThat(event.get("actor_id")).isEqualTo(fixture.actor);
        assertThat((String) event.get("payload")).contains("refundAuthorizationId", "reservationId", "amountCents", "origin", "authorizationMode", "authorizedBy")
                .doesNotContain("Private note");
        assertThatThrownBy(() -> manage.authorize(admin, decision.id(), "Again")).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(events()).hasSize(1);
    }

    @Test
    void theExecutionStepsAreAcceptedByTheGuardsAndAnExecutedRefundIsTerminal() {
        FinancialDecision decision = openCancellation();
        manage.recordRefundDecision(admin, decision.id());
        RefundAuthorization refund = manage.authorize(admin, decision.id(), "Reason");
        UUID collaborator = UUID.randomUUID();
        UUID movement = UUID.randomUUID();

        refunds.save(refund.startExecution(collaborator, OutflowMethod.TRANSFER, NOW));
        assertThat(refunds.find(fixture.tenant, refund.id()).orElseThrow().state()).isEqualTo(ExecutionState.EXECUTING);
        refunds.save(refunds.find(fixture.tenant, refund.id()).orElseThrow().undoStart());
        assertThat(refunds.find(fixture.tenant, refund.id()).orElseThrow()).isEqualTo(refund);
        RefundAuthorization done = refund.startExecution(collaborator, OutflowMethod.CASH, NOW).complete(movement, NOW.plusSeconds(5));
        refunds.save(refund.startExecution(collaborator, OutflowMethod.CASH, NOW));
        refunds.save(done);

        RefundAuthorization stored = refunds.find(fixture.tenant, refund.id()).orElseThrow();
        assertThat(stored.state()).isEqualTo(ExecutionState.EXECUTED);
        assertThat(stored.cashMovementId()).isEqualTo(movement);
        assertThatThrownBy(() -> fixture.jdbc.update("UPDATE commercial_reservation.refund_authorization SET reason = 'Other' WHERE refund_authorization_id = ?",
                refund.id())).hasMessageContaining("trg_refund_authorization_guard");
    }

    @Test
    void theDefaultAuthorizesBySystemWithAnEventThatHasNoAuthorAndAnotherTenantSeesNothing() {
        FinancialDecision decision = openCancellation();
        Clock later = Clock.fixed(NOW.plus(Duration.ofHours(24)), ZoneOffset.UTC);
        var automation = new RefundAutomationUseCase(decisions, refunds, new WeekdayBusinessCalendar(), new JdbcRefundAuthorizedOutbox(fixture.jdbc),
                new UuidGenerator(), new JdbcPaymentTransaction(fixture.transactions), later);

        RefundAuthorization refund = automation.applyDefault(fixture.tenant, decision.id());

        assertThat(decisions.find(fixture.tenant, decision.id()).orElseThrow().outcome()).isEqualTo(DecisionOutcome.REFUND_FULL);
        assertThat(refund.authorizedBy()).isNull();
        assertThat(events().get(0).get("actor_id")).isNull();
        assertThat(events().get(0).get("payload").toString()).contains("\"authorizedBy\": null");
        assertThat(decisions.find(UUID.randomUUID(), decision.id())).isEmpty();
        assertThat(refunds.find(UUID.randomUUID(), refund.id())).isEmpty();
    }

    @Test
    void aLateValueOnADecidedRefundCreatesOneMoreAuthorizationWithoutASecondDecision() {
        FinancialDecision decision = openCancellation();
        manage.recordRefundDecision(admin, decision.id());

        opening.open(fixture.tenant, reservationId, new Cents(2_500), new Cents(0), NOW);

        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.financial_decision WHERE reservation_id = ?", Integer.class,
                reservationId)).isEqualTo(1);
        RefundAuthorization increment = refunds.findByDecision(fixture.tenant, decision.id()).orElseThrow();
        assertThat(increment.authorizedAmount()).isEqualTo(new Cents(2_500));
        assertThat(increment.origin()).isEqualTo(RefundOrigin.CUSTOMER_CANCELLATION);
        assertThat(events()).hasSize(1);
    }
}
