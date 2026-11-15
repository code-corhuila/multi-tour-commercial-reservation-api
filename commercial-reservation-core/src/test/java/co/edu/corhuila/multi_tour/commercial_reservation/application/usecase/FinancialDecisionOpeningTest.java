package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder.Origin;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-016 (-api): AC-16.15, AC-16.16; plan D-05, D-08. One class is the real adapter of the four ports that the earlier stories left as doubles
// (013 FinancialDecisionOpener, 012 ExcessDecisionOpener and ExcessTreatment, 010 and 011 CancelledValueRecorder): one decision opens per recognized value,
// a value recognized again for an open decision updates its amount, and a late value on a decided refund follows that refund without a second decision.
class FinancialDecisionOpeningTest {

    private final RefundFakes fakes = new RefundFakes();
    private final FinancialDecisionOpening opening = new FinancialDecisionOpening(fakes.decisionRepository, fakes.refundRepository, fakes.calendar,
            fakes.outbox, fakes.ids, RefundFakes.CLOCK);
    private final UUID reservation = UUID.randomUUID();

    @Test
    void aRecognizedValueOpensOneDecisionAtItsRecognitionInstant() {
        ((FinancialDecisionOpener) opening).open(RefundFakes.TENANT, reservation, new Cents(80_000), new Cents(20_000), RefundFakes.NOW);

        FinancialDecision decision = fakes.decisions.values().iterator().next();
        assertThat(decision.kind()).isEqualTo(DecisionKind.CUSTOMER_CANCELLATION);
        assertThat(decision.baseAmount()).isEqualTo(new Cents(80_000));
        assertThat(decision.penalty()).isEqualTo(new Cents(20_000));
        assertThat(decision.recognizedAt()).isEqualTo(RefundFakes.NOW);
        assertThat(decision.outcome()).isNull();
    }

    @Test
    void theOtherPortsOpenTheirKindOfDecision() {
        opening.open(RefundFakes.TENANT, reservation, new Cents(5_000), RefundFakes.NOW);
        opening.open(RefundFakes.TENANT, UUID.randomUUID(), new Cents(7_000));
        opening.record(RefundFakes.TENANT, UUID.randomUUID(), new Cents(9_000), Origin.APPROVED_AFTER_CANCELLATION);
        opening.record(RefundFakes.TENANT, UUID.randomUUID(), new Cents(11_000), Origin.PENDING_TREATMENT_AFTER_EXPIRY);

        assertThat(fakes.decisions.values()).extracting(FinancialDecision::kind).containsExactly(DecisionKind.MODIFICATION_EXCESS,
                DecisionKind.MODIFICATION_EXCESS, DecisionKind.PENDING_TREATMENT, DecisionKind.PENDING_TREATMENT);
    }

    @Test
    void aValueRecognizedAgainForAnOpenDecisionUpdatesItsAmount() {
        opening.record(RefundFakes.TENANT, reservation, new Cents(30_000), Origin.PENDING_TREATMENT_AFTER_EXPIRY);
        opening.record(RefundFakes.TENANT, reservation, new Cents(4_000), Origin.APPROVED_AFTER_CANCELLATION);

        assertThat(fakes.decisions).hasSize(1);
        assertThat(fakes.decisions.values().iterator().next().baseAmount()).isEqualTo(new Cents(34_000));
        assertThat(fakes.refunds).isEmpty();
    }

    @Test
    void aLateValueOnADecidedRefundFollowsThatTreatmentWithoutASecondDecision() {
        opening.record(RefundFakes.TENANT, reservation, new Cents(30_000), Origin.PENDING_TREATMENT_AFTER_EXPIRY);
        FinancialDecision open = fakes.decisions.values().iterator().next();
        fakes.decisions.put(open.id(), open.recordRefundFull(UUID.randomUUID(), RefundFakes.NOW));

        opening.record(RefundFakes.TENANT, reservation, new Cents(4_000), Origin.APPROVED_AFTER_CANCELLATION);

        assertThat(fakes.decisions).hasSize(1);
        RefundAuthorization increment = fakes.refunds.values().iterator().next();
        assertThat(increment.authorizedAmount()).isEqualTo(new Cents(4_000));
        assertThat(increment.origin()).isEqualTo(RefundOrigin.PENDING_TREATMENT);
        assertThat(increment.financialDecisionId()).isEqualTo(open.id());
        assertThat(increment.authorizedBy()).isNull();
        assertThat(increment.executionDueAt()).isEqualTo(RefundFakes.NOW.plus(Duration.ofDays(5)));
        assertThat(fakes.events).containsExactly(increment);
    }

    @Test
    void aLateValueOnADecisionThatEndedAsCreditIsRefusedInsteadOfDropped() {
        opening.record(RefundFakes.TENANT, reservation, new Cents(30_000), Origin.PENDING_TREATMENT_AFTER_EXPIRY);
        FinancialDecision open = fakes.decisions.values().iterator().next();
        fakes.decisions.put(open.id(), new FinancialDecision(open.id(), open.tenantId(), open.reservationId(), null, open.kind(), open.baseAmount(), null,
                open.recognizedAt(), null, null, co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome.CREDIT_FULL, true, null,
                RefundFakes.NOW, null));

        assertThatThrownBy(() -> opening.record(RefundFakes.TENANT, reservation, new Cents(1_000), Origin.APPROVED_AFTER_CANCELLATION))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
