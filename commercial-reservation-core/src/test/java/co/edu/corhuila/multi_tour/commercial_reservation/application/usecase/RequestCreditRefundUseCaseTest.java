package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RequestCreditRefundCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-017 (-api): RN-10; AC-17.10, AC-17.11. The refund request of a credit opens a decision of the kind CREDIT_BALANCE_REFUND_REQUEST of HU-COM-016 for the
// amount asked, in the same transaction; the amount is checked against the available value first, staff gives a reason, the Final Customer asks only for their own credit,
// and the other roles are refused before anything is read. The amount set aside is the sum of the open requests, which the credit reads as one derived value.
class RequestCreditRefundUseCaseTest {

    private static final UUID CUSTOMER = new UUID(17, 3), OTHER_CUSTOMER = new UUID(17, 4), SOURCE = new UUID(17, 7), CREDIT = new UUID(17, 9);

    private final RefundFakes fakes = new RefundFakes();
    private final PaymentFakes payments = new PaymentFakes(new DepartureFakes());
    private final CreditBalance credit = new CreditBalance(CREDIT, RefundFakes.TENANT, SOURCE, CreditOrigin.CUSTOMER_CANCELLATION, new Cents(100_000), new UUID(17, 50), null,
            RefundFakes.NOW.minusSeconds(3_600), new UUID(17, 2), new Cents(20_000), new Cents(0));
    private final CreditBalanceRepository credits = new CreditBalanceRepository() {
        @Override public Optional<CreditBalance> find(UUID tenantId, UUID id) { return Optional.of(credit).filter(c -> c.tenantId().equals(tenantId) && c.id().equals(id)); }
        @Override public Optional<CreditBalance> findByDecision(UUID tenantId, UUID decisionId) { return Optional.empty(); }
        @Override public Optional<CreditBalance> findByAttempt(UUID tenantId, UUID attemptId) { return Optional.empty(); }
        @Override public void save(CreditBalance saved) { throw new AssertionError("a request never writes the credit: what is set aside is derived from the open requests"); }
    };
    private final RequestCreditRefundUseCase useCase = new RequestCreditRefundUseCase(credits, payments, fakes.decisionRepository, fakes.ids, fakes.transaction, RefundFakes.CLOCK);

    RequestCreditRefundUseCaseTest() {
        payments.reservations.put(SOURCE, Reservation.create(SOURCE, RefundFakes.TENANT, CUSTOMER, new UUID(17, 6), new UUID(17, 2), 2,
                new ReservedService(new UUID(17, 5), new Cents(100_000), new Cents(50_000)), new Cents(100_000), new Cents(100_000), List.of(), PaymentType.FULL_PAYMENT,
                PaymentMethod.TRANSFER, RefundFakes.NOW.minusSeconds(7_200)));
    }

    private RequestCreditRefundCommand command(Role role, UUID customer, long cents, String reason) {
        return new RequestCreditRefundCommand(RefundFakes.TENANT, new UUID(17, 2), role, customer, CREDIT, cents, reason);
    }

    @Test
    void aRefundRequestSetsTheAmountAsideAndOpensTheDecision() {
        FinancialDecision decision = useCase.request(command(Role.TENANT_ADMINISTRATOR, null, 30_000, "El cliente pide la devolución"));

        assertThat(decision.kind()).isEqualTo(DecisionKind.CREDIT_BALANCE_REFUND_REQUEST);
        assertThat(decision.creditBalanceId()).isEqualTo(CREDIT);
        assertThat(decision.reservationId()).isEqualTo(SOURCE);
        assertThat(decision.baseAmount()).isEqualTo(new Cents(30_000));
        assertThat(decision.isDecided()).isFalse();
        assertThat(decision.recognizedAt()).isEqualTo(RefundFakes.NOW);
        assertThat(fakes.decisions).containsValue(decision);
        assertThat(fakes.transactions).isEqualTo(1);
    }

    @Test
    void aRequestAboveTheAvailableOrWithoutReasonIsRefused() {
        assertThatThrownBy(() -> useCase.request(command(Role.TENANT_ADMINISTRATOR, null, 80_001, "Motivo"))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> useCase.request(command(Role.TENANT_ADMINISTRATOR, null, 1_000, " "))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> useCase.request(command(Role.OPERATIONAL_COLLABORATOR, null, 1_000, null))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> useCase.request(command(Role.TENANT_ADMINISTRATOR, null, 0, "Motivo"))).isInstanceOf(DomainException.class);
        assertThat(fakes.decisions).isEmpty();
    }

    @Test
    void theFinalCustomerAsksOnlyForTheirOwnCreditAndNeedsNoReason() {
        FinancialDecision own = useCase.request(command(Role.FINAL_CUSTOMER, CUSTOMER, 80_000, null));

        assertThat(own.baseAmount()).isEqualTo(new Cents(80_000));
        assertThatThrownBy(() -> useCase.request(command(Role.FINAL_CUSTOMER, OTHER_CUSTOMER, 1_000, null))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(fakes.decisions).hasSize(1);
    }

    @Test
    void managerAccountantAndAnalystCannotRequestAndAnotherTenantFindsNothing() {
        for (Role role : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatThrownBy(() -> useCase.request(command(role, null, 1_000, "Motivo"))).isInstanceOf(PaymentAccessDeniedException.class);
        }
        assertThatThrownBy(() -> useCase.request(new RequestCreditRefundCommand(UUID.randomUUID(), new UUID(17, 2), Role.TENANT_ADMINISTRATOR, null, CREDIT, 1_000, "Motivo")))
                .isInstanceOf(CreditBalanceNotFoundException.class);
        assertThat(fakes.decisions).isEmpty();
    }
}
