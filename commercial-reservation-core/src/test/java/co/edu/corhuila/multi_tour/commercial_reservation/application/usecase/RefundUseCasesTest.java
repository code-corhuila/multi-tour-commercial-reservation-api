package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-03 of HU-COM-016 (-api): AC-16.1 to AC-16.3, AC-16.7 to AC-16.11, AC-16.19. The use cases around the two aggregates, with in-memory doubles: only
// the Administrator decides, authorizes and rejects; the default, the determination and the overdue mark are the service token's; each write is one
// transaction with its RefundAuthorized event; a decision of another tenant looks missing.
class RefundUseCasesTest {

    private final RefundFakes fakes = new RefundFakes();
    private final ManageRefundDecisionsUseCase manage = new ManageRefundDecisionsUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar,
            fakes.outbox, fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final RefundAutomationUseCase automation = new RefundAutomationUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar,
            fakes.outbox, fakes.ids, fakes.transaction, RefundFakes.CLOCK);
    private final PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);

    @Test
    void theAdministratorRecordsTheRefundDecisionAndNothingElseDoes() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        FinancialDecision decided = manage.recordRefundDecision(admin, open.id());

        assertThat(decided.outcome()).isEqualTo(DecisionOutcome.REFUND_FULL);
        assertThat(decided.decidedBy()).isEqualTo(admin.actorId());
        assertThat(fakes.decisions.get(open.id())).isEqualTo(decided);
        for (Role other : new Role[] {Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER}) {
            FinancialDecision untouched = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 10_000);
            assertThatThrownBy(() -> manage.recordRefundDecision(RefundFakes.actor(other), untouched.id()))
                    .isInstanceOf(co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException.class);
            assertThat(fakes.decisions.get(untouched.id()).outcome()).isNull();
        }
    }

    @Test
    void aDecisionOfAnotherTenantLooksMissing() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        PaymentActor stranger = new PaymentActor(UUID.randomUUID(), UUID.randomUUID(), Role.TENANT_ADMINISTRATOR);

        assertThatThrownBy(() -> manage.recordRefundDecision(stranger, open.id())).isInstanceOf(RefundNotFoundException.class);
        assertThatThrownBy(() -> manage.authorize(stranger, open.id(), "Reason")).isInstanceOf(RefundNotFoundException.class);
    }

    @Test
    void authorizingCreatesTheRefundWithItsDueInstantAndOneEvent() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        manage.recordRefundDecision(admin, open.id());

        RefundAuthorization refund = manage.authorize(admin, open.id(), "The customer cancelled in time");

        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(80_000));
        assertThat(refund.executionDueAt()).isEqualTo(RefundFakes.NOW.plus(Duration.ofDays(5)));
        assertThat(refund.authorizedBy()).isEqualTo(admin.actorId());
        assertThat(fakes.refunds).containsValue(refund);
        assertThat(fakes.events).containsExactly(refund);
        assertThatThrownBy(() -> manage.authorize(admin, open.id(), "Again")).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakes.events).hasSize(1);
        assertThatThrownBy(() -> manage.authorize(RefundFakes.actor(Role.OPERATIONAL_COLLABORATOR), open.id(), "Reason"))
                .isInstanceOf(co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException.class);
    }

    @Test
    void aDecisionWithoutTheRefundOutcomeCannotBeAuthorizedAndNothingIsWritten() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        assertThatThrownBy(() -> manage.authorize(admin, open.id(), "Reason")).isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.refunds).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void onlyACreditBalanceRequestIsRejectedWithAJustification() {
        FinancialDecision request = fakes.open(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST, 50_000);
        FinancialDecision cancellation = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 50_000);

        FinancialDecision rejected = manage.reject(admin, request.id(), "The balance is not refundable");

        assertThat(rejected.outcome()).isEqualTo(DecisionOutcome.REJECTED);
        assertThatThrownBy(() -> manage.reject(admin, cancellation.id(), "No")).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> manage.reject(RefundFakes.actor(Role.MANAGER), request.id(), "No"))
                .isInstanceOf(co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException.class);
    }

    @Test
    void theDefaultIsAFullRefundCountedAsAuthorizationAndIsRefusedWhenFrozenOrNotDue() {
        FinancialDecision due = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        FinancialDecision frozen = fakes.open(DecisionKind.PENDING_TREATMENT, 30_000);
        fakes.decisions.put(frozen.id(), frozen.freeze(RefundFakes.NOW.plus(Duration.ofHours(1))));
        UnderClock later = new UnderClock(fakes, RefundFakes.NOW.plus(Duration.ofHours(24)));

        RefundAuthorization refund = later.automation.applyDefault(RefundFakes.TENANT, due.id());

        assertThat(fakes.decisions.get(due.id()).decidedBySystem()).isTrue();
        assertThat(refund.authorizedBy()).isNull();
        assertThat(refund.authorizedAmount()).isEqualTo(new Cents(80_000));
        assertThat(fakes.events).containsExactly(refund);
        assertThatThrownBy(() -> later.automation.applyDefault(RefundFakes.TENANT, frozen.id())).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> automation.applyDefault(RefundFakes.TENANT, fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 1).id()))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.refunds).hasSize(1);
    }

    @Test
    void notAttributableToTheCustomerIsRefundedInFullAndAuthorizedAutomatically() {
        UUID reservation = UUID.randomUUID();

        RefundAuthorization refund = automation.determine(RefundFakes.TENANT, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(300_000));

        assertThat(refund.reservationId()).isEqualTo(reservation);
        assertThat(refund.authorizedBy()).isNull();
        assertThat(refund.financialDecisionId()).isNull();
        assertThat(fakes.events).containsExactly(refund);
    }

    @Test
    void anOverdueMarkKeepsTheRefundPendingAndIsRefusedWhileNotDue() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        manage.recordRefundDecision(admin, open.id());
        RefundAuthorization refund = manage.authorize(admin, open.id(), "Reason");
        UnderClock later = new UnderClock(fakes, refund.executionDueAt());

        assertThatThrownBy(() -> automation.markOverdue(RefundFakes.TENANT, refund.id())).isInstanceOf(BusinessRuleViolation.class);
        RefundAuthorization marked = later.automation.markOverdue(RefundFakes.TENANT, refund.id());

        assertThat(marked.overdueMarkedAt()).isEqualTo(refund.executionDueAt());
        assertThat(marked.state()).isEqualTo(ExecutionState.AUTHORIZED);
        assertThatThrownBy(() -> automation.markOverdue(UUID.randomUUID(), refund.id())).isInstanceOf(RefundNotFoundException.class);
    }

    // HU-COM-016 DP-02 (Project Lead, 2026-11-12): the five business days count from the determination (recognizedAt of the decision), not from the authorization.
    @Test
    void theFiveBusinessDaysOfAnAuthorizedRefundCountFromTheDeterminationNotFromTheAuthorization() {
        java.time.Instant recognized = RefundFakes.NOW.minus(Duration.ofHours(3));
        FinancialDecision open = fakes.openAt(DecisionKind.CUSTOMER_CANCELLATION, 80_000, recognized);
        manage.recordRefundDecision(admin, open.id());

        RefundAuthorization refund = manage.authorize(admin, open.id(), "The customer cancelled in time");

        assertThat(refund.authorizedAt()).isEqualTo(RefundFakes.NOW);
        assertThat(refund.executionDueAt()).isEqualTo(recognized.plus(Duration.ofDays(5)));
    }

    @Test
    void theRefundOfTheDefaultAfter24HoursAlsoCountsFromTheDetermination() {
        FinancialDecision due = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        UnderClock later = new UnderClock(fakes, RefundFakes.NOW.plus(Duration.ofHours(24)));

        RefundAuthorization refund = later.automation.applyDefault(RefundFakes.TENANT, due.id());

        assertThat(refund.executionDueAt()).isEqualTo(RefundFakes.NOW.plus(Duration.ofDays(5)));
    }

    // Simplest behaviour, reported as a question to the Project Lead: a decision frozen and resumed keeps its recognition instant, so the five days still count from it.
    @Test
    void aResumedDecisionCountsTheFiveDaysFromItsRecognitionInstantToo() {
        java.time.Instant recognized = RefundFakes.NOW.minus(Duration.ofHours(10));
        FinancialDecision open = fakes.openAt(DecisionKind.CUSTOMER_CANCELLATION, 80_000, recognized);
        fakes.decisions.put(open.id(), open.freeze(RefundFakes.NOW.minus(Duration.ofHours(5))).resumed(RefundFakes.NOW.minus(Duration.ofHours(1))));
        manage.recordRefundDecision(admin, open.id());

        RefundAuthorization refund = manage.authorize(admin, open.id(), "Resumed");

        assertThat(refund.executionDueAt()).isEqualTo(recognized.plus(Duration.ofDays(5)));
    }

    // The same doubles under a later clock.
    private static final class UnderClock {
        final RefundAutomationUseCase automation;

        UnderClock(RefundFakes fakes, java.time.Instant at) {
            this.automation = new RefundAutomationUseCase(fakes.decisionRepository, fakes.refundRepository, fakes.calendar, fakes.outbox, fakes.ids,
                    fakes.transaction, java.time.Clock.fixed(at, java.time.ZoneOffset.UTC));
        }
    }
}
