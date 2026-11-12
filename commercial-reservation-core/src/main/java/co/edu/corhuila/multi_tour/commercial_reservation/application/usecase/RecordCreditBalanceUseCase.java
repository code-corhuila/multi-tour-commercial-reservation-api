package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalanceRecorded;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// HU-COM-017 RN-02; plan D-03: the Administrator decides the 100 % credit of an open decision. The outcome and the credit balance are written in one transaction,
// so a decided decision always has its credit; no amount travels in the call (the base amount of the decision is the credit) and no refund is created.
public class RecordCreditBalanceUseCase {

    private final FinancialDecisionRepository decisions;
    private final CreditBalanceRepository credits;
    private final IdGenerator ids;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public RecordCreditBalanceUseCase(FinancialDecisionRepository decisions, CreditBalanceRepository credits, IdGenerator ids, PaymentEventOutbox outbox,
            PaymentTransaction transaction, Clock clock) {
        this.decisions = decisions;
        this.credits = credits;
        this.ids = ids;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    public CreditBalance recordCreditDecision(PaymentActor actor, UUID financialDecisionId) {
        RefundAuthorizationRules.requireAdministrator(actor);
        FinancialDecision decision = decisions.find(actor.tenantId(), financialDecisionId)
                .orElseThrow(() -> new RefundNotFoundException("financial decision", financialDecisionId));
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision decided = decision.recordCreditFull(actor.actorId(), now);
            CreditBalance credit = CreditBalance.ofDecision(ids.newId(), decided, actor.actorId(), now);
            decisions.save(decided);
            credits.save(credit);
            outbox.append(new CreditBalanceRecorded(credit.tenantId(), credit.sourceReservationId(), now, credit.id(), credit.amount(), credit.origin(), actor.actorId()));
            return credit;
        });
    }
}
