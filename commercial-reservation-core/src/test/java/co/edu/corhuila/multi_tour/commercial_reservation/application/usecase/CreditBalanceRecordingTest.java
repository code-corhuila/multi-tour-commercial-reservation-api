package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalanceRecorded;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-017 (-api): RN-02, RN-03; AC-17.1, AC-17.2, AC-17.3. The Administrator decides the 100 % credit of an open decision once, and the decision and
// the credit commit together; the excess of a validated payment becomes one credit per payment attempt, so a repeat records nothing more, and it is never applied
// by itself. The persistence adapter and the routes are later pull requests; here the port has an in-memory double.
class CreditBalanceRecordingTest {

    private final RefundFakes fakes = new RefundFakes();
    private final Map<UUID, CreditBalance> credits = new LinkedHashMap<>();
    private final CreditBalanceRepository repository = new CreditBalanceRepository() {
        @Override public Optional<CreditBalance> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(credits.get(id)).filter(c -> c.tenantId().equals(tenantId));
        }
        @Override public Optional<CreditBalance> findByDecision(UUID tenantId, UUID decisionId) {
            return credits.values().stream().filter(c -> c.tenantId().equals(tenantId) && decisionId.equals(c.sourceDecisionId())).findFirst();
        }
        @Override public Optional<CreditBalance> findByAttempt(UUID tenantId, UUID attemptId) {
            return credits.values().stream().filter(c -> c.tenantId().equals(tenantId) && attemptId.equals(c.sourceAttemptId())).findFirst();
        }
        @Override public void save(CreditBalance credit) { credits.put(credit.id(), credit); }
    };
    private final List<PaymentEvent> events = new ArrayList<>();
    private final RecordCreditBalanceUseCase useCase = new RecordCreditBalanceUseCase(fakes.decisionRepository, repository, fakes.ids, events::add,
            fakes.transaction, RefundFakes.CLOCK);
    private final CreditBalanceRecording excess = new CreditBalanceRecording(repository, fakes.ids, events::add, RefundFakes.CLOCK);

    @Test
    void onlyTheAdministratorDecidesTheCreditAndOnlyOnce() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);

        assertThatThrownBy(() -> useCase.recordCreditDecision(RefundFakes.actor(Role.OPERATIONAL_COLLABORATOR), open.id()))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(credits).isEmpty();

        PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);
        CreditBalance credit = useCase.recordCreditDecision(admin, open.id());

        assertThat(credit.amount()).isEqualTo(new Cents(80_000));
        assertThat(credit.origin()).isEqualTo(CreditOrigin.CUSTOMER_CANCELLATION);
        assertThat(credit.recordedBy()).isEqualTo(admin.actorId());
        assertThat(fakes.decisions.get(open.id()).outcome()).isEqualTo(DecisionOutcome.CREDIT_FULL);
        assertThat(fakes.transactions).isEqualTo(1);
        assertThatThrownBy(() -> useCase.recordCreditDecision(admin, open.id())).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(credits).hasSize(1);
    }

    @Test
    void theExcessOfAValidatedPaymentBecomesOneCreditBalancePerAttempt() {
        UUID reservation = UUID.randomUUID(), attempt = UUID.randomUUID();

        excess.recordExcess(RefundFakes.TENANT, reservation, attempt, new Cents(15_000));
        excess.recordExcess(RefundFakes.TENANT, reservation, attempt, new Cents(15_000));

        assertThat(credits).hasSize(1);
        CreditBalance credit = credits.values().iterator().next();
        assertThat(credit.origin()).isEqualTo(CreditOrigin.PAYMENT_EXCESS);
        assertThat(credit.sourceAttemptId()).isEqualTo(attempt);
        assertThat(credit.sourceReservationId()).isEqualTo(reservation);
        assertThat(credit.amount()).isEqualTo(new Cents(15_000));
        assertThat(credit.recordedAt()).isEqualTo(RefundFakes.NOW);
    }

    @Test
    void anExcessIsNeverAppliedByItself() {
        excess.recordExcess(RefundFakes.TENANT, UUID.randomUUID(), UUID.randomUUID(), new Cents(15_000));
        excess.recordExcess(RefundFakes.TENANT, UUID.randomUUID(), UUID.randomUUID(), new Cents(5_000));

        assertThat(credits.values()).allSatisfy(credit -> {
            assertThat(credit.applied()).isEqualTo(new Cents(0));
            assertThat(credit.available()).isEqualTo(credit.amount());
        });
    }

    // T-06 / AC-17.17, RNF-06: one event per recording, in the same transaction, with identifiers, the amount and the origin; a repeat of the excess emits nothing.
    @Test
    void recordingEmitsOneEventWithoutPersonalData() {
        FinancialDecision open = fakes.open(DecisionKind.CUSTOMER_CANCELLATION, 80_000);
        PaymentActor admin = RefundFakes.actor(Role.TENANT_ADMINISTRATOR);

        CreditBalance credit = useCase.recordCreditDecision(admin, open.id());

        assertThat(events).singleElement().isInstanceOfSatisfying(CreditBalanceRecorded.class, event -> {
            assertThat(event.creditBalanceId()).isEqualTo(credit.id());
            assertThat(event.reservationId()).isEqualTo(credit.sourceReservationId());
            assertThat(event.amount()).isEqualTo(new Cents(80_000));
            assertThat(event.origin()).isEqualTo(CreditOrigin.CUSTOMER_CANCELLATION);
            assertThat(event.actorId()).isEqualTo(admin.actorId());
            assertThat(event.occurredAt()).isEqualTo(RefundFakes.NOW);
        });
    }

    @Test
    void theExcessEmitsOneEventAndARepeatEmitsNothingMore() {
        UUID reservation = UUID.randomUUID(), attempt = UUID.randomUUID();

        excess.recordExcess(RefundFakes.TENANT, reservation, attempt, new Cents(15_000));
        excess.recordExcess(RefundFakes.TENANT, reservation, attempt, new Cents(15_000));

        assertThat(events).singleElement().isInstanceOfSatisfying(CreditBalanceRecorded.class, event -> {
            assertThat(event.reservationId()).isEqualTo(reservation);
            assertThat(event.origin()).isEqualTo(CreditOrigin.PAYMENT_EXCESS);
            assertThat(event.actorId()).isNull();
        });
    }
}
