package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Closing;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// T-02 of HU-COM-019 (-api): AC-19.4, AC-19.5; DD-2. The definitive closing of a cancelled reservation is read from the decisions of HU-COM-016, the executed refunds
// and the credit balances of HU-COM-017, scoped to the tenant. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationClosingPersistenceTest {
    private static final Instant RECOGNIZED = Instant.parse("2026-02-01T14:00:00Z");
    private static final Instant REFUNDED = Instant.parse("2026-02-04T14:00:00Z");
    private static final Instant CREDITED = Instant.parse("2026-02-09T10:00:00Z");
    private final UUID reservation = UUID.randomUUID();
    private ReservationPersistenceFixture fixture;
    private JdbcReservationClosingReader closings;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        closings = new JdbcReservationClosingReader(fixture.jdbc);
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc))
                .save(fixture.reservation(reservation, true));
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CANCELLED', cancellation_cause = 'CUSTOMER_REQUEST', cancelled_at = ? "
                + "WHERE reservation_id = ?", at(RECOGNIZED), reservation);
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private UUID decision() {
        return decision("CUSTOMER_CANCELLATION");
    }

    private UUID decision(String kind) {
        UUID id = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.financial_decision (financial_decision_id, tenant_id, reservation_id, decision_kind, base_amount_cents, "
                + "penalty_amount_cents, recognized_at) VALUES (?, ?, ?, ?, 80000, ?, ?)", id, fixture.tenant, reservation, kind, kind.equals("CUSTOMER_CANCELLATION") ? 0L : null, at(RECOGNIZED));
        return id;
    }

    private void decide(UUID decision, String outcome) {
        fixture.jdbc.update("UPDATE commercial_reservation.financial_decision SET outcome = ?, decided_by_system = true, decided_at = ? WHERE financial_decision_id = ?",
                outcome, at(RECOGNIZED.plusSeconds(60)), decision);
    }

    private void executedRefund(UUID decision) {
        fixture.jdbc.update("INSERT INTO commercial_reservation.refund_authorization (refund_authorization_id, tenant_id, reservation_id, refund_origin, "
                + "financial_decision_id, potential_refund_value_cents, authorized_amount_cents, reason, authorization_mode, authorized_at, execution_due_at, execution_state, "
                + "executing_at, executed_by, outflow_method, executed_at, cash_movement_id) VALUES (?, ?, ?, 'CUSTOMER_CANCELLATION', ?, 80000, 80000, 'Reembolso ficticio', "
                + "'AUTOMATIC_DETERMINATION', ?, ?, 'EXECUTED', ?, ?, 'TRANSFER', ?, ?)", UUID.randomUUID(), fixture.tenant, reservation, decision, at(RECOGNIZED),
                at(RECOGNIZED.plusSeconds(3600)), at(REFUNDED.minusSeconds(60)), fixture.actor, at(REFUNDED), UUID.randomUUID());
    }

    private void recordedCredit(UUID decision) {
        fixture.jdbc.update("INSERT INTO commercial_reservation.credit_balance (credit_balance_id, tenant_id, source_reservation_id, credit_origin, amount_cents, "
                + "source_financial_decision_id, recorded_at, recorded_by) VALUES (?, ?, ?, 'CUSTOMER_CANCELLATION', 80000, ?, ?, ?)", UUID.randomUUID(), fixture.tenant,
                reservation, decision, at(CREDITED), fixture.actor);
    }

    @Test
    void aCancelledReservationWithNoDecisionHasNoClosing() {
        assertThat(closings.closingOf(fixture.tenant, reservation)).isEmpty();
    }

    @Test
    void aDecisionStillOpenLeavesTheReservationNotClosed() {
        decision();
        assertThat(closings.closingOf(fixture.tenant, reservation)).isEmpty();
    }

    @Test
    void aDecidedRefundNotYetExecutedLeavesTheReservationNotClosed() {
        decide(decision(), "REFUND_FULL");
        assertThat(closings.closingOf(fixture.tenant, reservation)).isEmpty();
    }

    @Test
    void anExecutedRefundClosesTheReservationAtItsExecution() {
        UUID decision = decision();
        decide(decision, "REFUND_FULL");
        executedRefund(decision);
        assertThat(closings.closingOf(fixture.tenant, reservation)).contains(new Closing(REFUNDED, false));
    }

    @Test
    void aRecordedCreditBalanceClosesTheReservationAtItsRecording() {
        UUID decision = decision();
        decide(decision, "CREDIT_FULL");
        recordedCredit(decision);
        assertThat(closings.closingOf(fixture.tenant, reservation)).contains(new Closing(CREDITED, false));
    }

    @Test
    void theClosingIsTheLatestSettlementOfAllTheDecisions() {
        UUID refunded = decision();
        decide(refunded, "REFUND_FULL");
        executedRefund(refunded);
        UUID credited = decision("MODIFICATION_EXCESS");
        decide(credited, "CREDIT_FULL");
        recordedCredit(credited);
        assertThat(closings.closingOf(fixture.tenant, reservation)).contains(new Closing(CREDITED, false));
    }

    @Test
    void aDecisionFrozenByTheInactivationBlocksTheClosing() {
        UUID decision = decision();
        fixture.jdbc.update("UPDATE commercial_reservation.financial_decision SET frozen_at = ?, remaining_at_freeze_minutes = 600 WHERE financial_decision_id = ?",
                at(RECOGNIZED.plusSeconds(120)), decision);
        assertThat(closings.closingOf(fixture.tenant, reservation)).contains(new Closing(null, true));
    }

    @Test
    void anotherTenantNeverReadsTheClosing() {
        UUID decision = decision();
        decide(decision, "REFUND_FULL");
        executedRefund(decision);
        assertThat(closings.closingOf(UUID.randomUUID(), reservation)).isEmpty();
    }
}
