package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-01 and T-02 of HU-COM-016 (-api): RN-01, RN-02, RN-06, RN-08; AC-16.1, AC-16.8, AC-16.9, AC-16.10. The decision of the 24 running hours:
// the outcome is recorded once with the person and the instant, nothing can change the amount of a decided one, a frozen one is never
// defaulted and only a credit-balance request can be rejected. Pure: the inputs are ids, cents and instants.
class FinancialDecisionTest {

    private static final Instant RECOGNIZED = Instant.parse("2026-11-10T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private static FinancialDecision cancellation() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CUSTOMER_CANCELLATION,
                new Cents(80_000), new Cents(20_000), null, RECOGNIZED);
    }

    private static FinancialDecision creditRequest() {
        return FinancialDecision.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DecisionKind.CREDIT_BALANCE_REFUND_REQUEST,
                new Cents(50_000), null, UUID.randomUUID(), RECOGNIZED);
    }

    @Test
    void aDecisionIsRecordedOnceWithThePersonAndTheInstant() {
        FinancialDecision decided = cancellation().recordRefundFull(ADMIN, RECOGNIZED.plusSeconds(60));

        assertThat(decided.outcome()).isEqualTo(DecisionOutcome.REFUND_FULL);
        assertThat(decided.decidedBy()).isEqualTo(ADMIN);
        assertThat(decided.decidedBySystem()).isFalse();
        assertThat(decided.decidedAt()).isEqualTo(RECOGNIZED.plusSeconds(60));
        assertThat(decided.baseAmount()).isEqualTo(new Cents(80_000));
        assertThatThrownBy(() -> decided.recordRefundFull(ADMIN, RECOGNIZED.plusSeconds(120)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void theAmountOfADecisionIsFixedAndOnlyAnOpenOneTakesALateValue() {
        FinancialDecision open = cancellation().withLateValue(new Cents(5_000));

        assertThat(open.baseAmount()).isEqualTo(new Cents(85_000));
        assertThatThrownBy(() -> open.recordRefundFull(ADMIN, RECOGNIZED).withLateValue(new Cents(1)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aDecisionNeedsItsKindItsPositiveBaseAndTheCreditReferenceOfARequestOnly() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> FinancialDecision.open(id, id, id, DecisionKind.CUSTOMER_CANCELLATION, new Cents(0), null, null, RECOGNIZED))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FinancialDecision.open(id, id, id, DecisionKind.PENDING_TREATMENT, new Cents(10), new Cents(1), null, RECOGNIZED))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FinancialDecision.open(id, id, id, DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, new Cents(10), null, null, RECOGNIZED))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FinancialDecision.open(id, id, id, DecisionKind.MODIFICATION_EXCESS, new Cents(10), null, id, RECOGNIZED))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void onlyACreditBalanceRequestCanBeRejectedWithAJustification() {
        FinancialDecision rejected = creditRequest().reject(ADMIN, "  The balance is not refundable  ", RECOGNIZED.plusSeconds(5));

        assertThat(rejected.outcome()).isEqualTo(DecisionOutcome.REJECTED);
        assertThat(rejected.rejectionJustification()).isEqualTo("The balance is not refundable");
        assertThatThrownBy(() -> creditRequest().reject(ADMIN, " ", RECOGNIZED)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> creditRequest().reject(ADMIN, "x".repeat(501), RECOGNIZED)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> cancellation().reject(ADMIN, "No", RECOGNIZED)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void twentyFourRunningHoursCountFromRecognition() {
        FinancialDecision open = cancellation();

        assertThat(open.dueAt()).isEqualTo(RECOGNIZED.plus(Duration.ofHours(24)));
        assertThat(open.isDue(RECOGNIZED.plus(Duration.ofHours(24)).minusSeconds(1))).isFalse();
        assertThat(open.isDue(RECOGNIZED.plus(Duration.ofHours(24)))).isTrue();
        assertThat(open.recordRefundFull(ADMIN, RECOGNIZED).isDue(RECOGNIZED.plus(Duration.ofDays(3)))).isFalse();
    }

    @Test
    void theDefaultIsAFullRefundDecidedBySystemAndOnlyWhenDue() {
        FinancialDecision open = cancellation();
        Instant due = open.dueAt();

        FinancialDecision defaulted = open.applyDefault(due);

        assertThat(defaulted.outcome()).isEqualTo(DecisionOutcome.REFUND_FULL);
        assertThat(defaulted.decidedBySystem()).isTrue();
        assertThat(defaulted.decidedBy()).isNull();
        assertThatThrownBy(() -> open.applyDefault(due.minusSeconds(1))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> defaulted.applyDefault(due)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aFrozenDecisionIsNotDefaultedAndKeepsItsRemainingTime() {
        FinancialDecision frozen = cancellation().freeze(RECOGNIZED.plus(Duration.ofHours(10)));

        assertThat(frozen.remainingAtFreezeMinutes()).isEqualTo(14 * 60);
        assertThat(frozen.isDue(RECOGNIZED.plus(Duration.ofDays(30)))).isFalse();
        assertThatThrownBy(() -> frozen.applyDefault(RECOGNIZED.plus(Duration.ofDays(30)))).isInstanceOf(BusinessRuleViolation.class);
    }

    // HU-COM-016 DP-13 out (Project Lead, 2026-11-12; signature of HU-WRK-002, 2026-11-14): a credit-balance refund request has no 24-hour default. It is never
    // due, the default is refused, and it stays open until a person authorizes or rejects it, even long after the 24 hours.
    @Test
    void aCreditRequestIsNeverDefaultedAndStaysOpenForAPerson() {
        FinancialDecision request = creditRequest();
        Instant late = RECOGNIZED.plus(Duration.ofDays(30));

        assertThat(request.isDue(late)).isFalse();
        assertThatThrownBy(() -> request.applyDefault(late)).isInstanceOf(BusinessRuleViolation.class);
        FinancialDecision authorized = request.authorizeRequest(ADMIN, late);
        assertThat(authorized.outcome()).isEqualTo(DecisionOutcome.AUTHORIZED);
        assertThat(authorized.decidedBySystem()).isFalse();
        assertThat(request.reject(ADMIN, "Balance already used", late).outcome()).isEqualTo(DecisionOutcome.REJECTED);
    }
}
