package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RequestCreditRefundCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DecisionKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// HU-COM-017 RN-10; AC-17.10, AC-17.11: the refund request of a credit opens, in one transaction, a decision of the kind CREDIT_BALANCE_REFUND_REQUEST of HU-COM-016
// for the amount asked. The role and the ownership are checked before anything else, then the amount against the available value, then the reason when staff asks.
// What is set aside is derived from the open requests (the credit reads it), so nothing is written to the credit; a rejection releases it by itself.
public class RequestCreditRefundUseCase {

    private final CreditBalanceRepository credits;
    private final ReservationRepository reservations;
    private final FinancialDecisionRepository decisions;
    private final IdGenerator ids;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public RequestCreditRefundUseCase(CreditBalanceRepository credits, ReservationRepository reservations, FinancialDecisionRepository decisions, IdGenerator ids,
            PaymentTransaction transaction, Clock clock) {
        this.credits = credits;
        this.reservations = reservations;
        this.decisions = decisions;
        this.ids = ids;
        this.transaction = transaction;
        this.clock = clock;
    }

    public FinancialDecision request(RequestCreditRefundCommand command) {
        PaymentAuthorization.requireRegister(new PaymentActor(command.tenantId(), command.actorId(), command.role(), Set.of()));
        Cents amount = Cents.of("amountCents", command.amountCents());
        if (amount.amount() == 0) throw new DomainException("amountCents", "amountCents must be above zero");
        boolean staff = command.role() != Role.FINAL_CUSTOMER;
        if (staff && (command.reason() == null || command.reason().isBlank())) throw new DomainException("reason", "reason is required when staff asks");
        return transaction.inTransaction(() -> {
            CreditBalance credit = credits.find(command.tenantId(), command.creditBalanceId()).orElseThrow(() -> new CreditBalanceNotFoundException(command.creditBalanceId()));
            UUID holder = reservations.findByTenantAndId(command.tenantId(), credit.sourceReservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(credit.sourceReservationId())).customerId();
            if (!staff && !holder.equals(command.actorCustomerId())) throw new PaymentAccessDeniedException();
            if (amount.amount() > credit.available().amount()) throw new BusinessRuleViolation("the amount is above the available value of the credit balance");
            FinancialDecision decision = FinancialDecision.open(ids.newId(), command.tenantId(), credit.sourceReservationId(), DecisionKind.CREDIT_BALANCE_REFUND_REQUEST,
                    amount, null, credit.id(), Instant.now(clock));
            decisions.save(decision);
            return decision;
        });
    }
}
