package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreditBalanceRecording;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ExecuteRefundUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ManageRefundDecisionsUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-015 AC-15.4, AC-15.8, RN-06 over the real schema: an executed refund and a recorded credit balance each write one ClientNotified row with the closed
// payload the database accepts, a repeat leaves one, and the reader gives the amount of the fact. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RefundAndCreditNoticePersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcRefundAuthorizationRepository refunds;
    private JdbcCreditBalanceRepository credits;
    private ManageRefundDecisionsUseCase manage;
    private ExecuteRefundUseCase execute;
    private FinancialDecisionOpening opening;
    private JdbcFinancialDecisionRepository decisions;
    private JdbcClientNoticeReader reader;
    private PaymentActor admin;
    private UUID reservationId;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        decisions = new JdbcFinancialDecisionRepository(fixture.jdbc);
        refunds = new JdbcRefundAuthorizationRepository(fixture.jdbc);
        credits = new JdbcCreditBalanceRepository(fixture.jdbc);
        reader = new JdbcClientNoticeReader(fixture.jdbc);
        var calendar = new WeekdayBusinessCalendar();
        var outbox = new JdbcRefundAuthorizedOutbox(fixture.jdbc);
        var ids = new UuidGenerator();
        var transaction = new JdbcPaymentTransaction(fixture.transactions);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        opening = new FinancialDecisionOpening(decisions, refunds, calendar, outbox, ids, clock);
        manage = new ManageRefundDecisionsUseCase(decisions, refunds, calendar, outbox, ids, transaction, clock);
        execute = new ExecuteRefundUseCase(refunds, transaction, clock, new JdbcClientNoticeOutbox(fixture.jdbc));
        admin = new PaymentActor(fixture.tenant, fixture.actor, Role.TENANT_ADMINISTRATOR);
        reservationId = UUID.randomUUID();
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc))
                .save(fixture.reservation(reservationId, true));
    }

    private RefundAuthorization authorizedRefund() {
        opening.open(fixture.tenant, reservationId, new Cents(80_000), new Cents(20_000), NOW);
        FinancialDecision decision = decisions.findLatest(fixture.tenant, reservationId, DecisionKind.CUSTOMER_CANCELLATION).orElseThrow();
        manage.recordRefundDecision(admin, decision.id());
        return manage.authorize(admin, decision.id(), "Private note of the administrator");
    }

    private List<Map<String, Object>> notices(String type) {
        return fixture.jdbc.queryForList("SELECT outbox_event_id, payload::text AS payload FROM commercial_reservation.outbox_event WHERE tenant_id = ? "
                + "AND reservation_id = ? AND event_type = 'ClientNotified' AND payload ->> 'notificationType' = ?", fixture.tenant, reservationId, type);
    }

    @Test
    void anExecutedRefundWritesOneNoticeWithTheClosedPayloadAndARedeliveryLeavesOne() {
        RefundAuthorization refund = authorizedRefund();
        execute.start(fixture.tenant, refund.id(), fixture.actor, OutflowMethod.TRANSFER);
        UUID movement = UUID.randomUUID();

        execute.complete(fixture.tenant, refund.id(), movement);
        execute.complete(fixture.tenant, refund.id(), movement);

        assertThat(notices("REFUND_PROCESSED")).hasSize(1);
        String payload = (String) notices("REFUND_PROCESSED").get(0).get("payload");
        assertThat(payload).contains("RefundExecuted", refund.id().toString(), fixture.customer.toString()).doesNotContain("amount", "reason", "@");
    }

    @Test
    void aRefundThatIsOnlyStartedNotifiesNothing() {
        RefundAuthorization refund = authorizedRefund();

        execute.start(fixture.tenant, refund.id(), fixture.actor, OutflowMethod.CASH);

        assertThat(notices("REFUND_PROCESSED")).isEmpty();
    }

    @Test
    void theReaderGivesTheAuthorizedAmountOfTheRefund() {
        RefundAuthorization refund = authorizedRefund();
        execute.start(fixture.tenant, refund.id(), fixture.actor, OutflowMethod.TRANSFER);
        execute.complete(fixture.tenant, refund.id(), UUID.randomUUID());
        UUID notice = (UUID) notices("REFUND_PROCESSED").get(0).get("outbox_event_id");

        StoredNotice stored = reader.find(fixture.tenant, notice).orElseThrow();

        assertThat(stored.type()).isEqualTo(NoticeType.REFUND_PROCESSED);
        assertThat(stored.amountCents()).isEqualTo(refund.authorizedAmount().amount());
    }

    @Test
    void aRecordedCreditBalanceWritesOneNoticeAndARepeatedExcessLeavesOneWithItsAmount() {
        UUID attempt = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.payment_attempt (payment_attempt_id, tenant_id, reservation_id, payment_method, "
                + "received_amount_cents, validation_status, payment_status_snapshot, registered_by, registered_at) "
                + "VALUES (?, ?, ?, 'TRANSFER', 100000, 'IN_VALIDATION', 'NO_PAYMENT', ?, now())", attempt, fixture.tenant, reservationId, fixture.actor);
        var recording = new CreditBalanceRecording(credits, new UuidGenerator(),
                new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc)), Clock.fixed(NOW, ZoneOffset.UTC));

        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));
        recording.recordExcess(fixture.tenant, reservationId, attempt, new Cents(12_000));

        assertThat(notices("CREDIT_BALANCE_RECORDED")).hasSize(1);
        String payload = (String) notices("CREDIT_BALANCE_RECORDED").get(0).get("payload");
        assertThat(payload).contains("CreditBalanceRecorded").doesNotContain("amount", "reason", "@");
        StoredNotice stored = reader.find(fixture.tenant, (UUID) notices("CREDIT_BALANCE_RECORDED").get(0).get("outbox_event_id")).orElseThrow();
        assertThat(stored.amountCents()).isEqualTo(12_000L);
    }
}
