package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplyCreditCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCreditBalances;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditApplicationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.UUID;

// HU-COM-017 plan D-06: the entry of the routes. A Final Customer is mapped from their account to their customer record (an unlinked account looks like a missing
// credit, AC-17.14); the two acts themselves are the use cases that already hold the rules.
public class ManageCreditBalancesUseCase implements ManageCreditBalances {

    private final RecordCreditBalanceUseCase record;
    private final ApplyCreditBalanceUseCase apply;
    private final CreditBalanceRepository credits;
    private final CreditApplicationReader applications;
    private final CustomerByAccount accounts;
    private final ReservationRepository reservations;

    public ManageCreditBalancesUseCase(RecordCreditBalanceUseCase record, ApplyCreditBalanceUseCase apply, CreditBalanceRepository credits,
            CreditApplicationReader applications, CustomerByAccount accounts,
            ReservationRepository reservations) {
        this.record = record;
        this.apply = apply;
        this.credits = credits;
        this.applications = applications;
        this.accounts = accounts;
        this.reservations = reservations;
    }

    @Override
    public CreditBalanceView recordCreditDecision(PaymentActor actor, UUID financialDecisionId) {
        return view(actor, record.recordCreditDecision(actor, financialDecisionId));
    }

    @Override
    public CreditApplication apply(PaymentActor actor, UUID creditBalanceId, UUID targetReservationId, long amountCents) {
        UUID customer = actor.role() == Role.FINAL_CUSTOMER
                ? accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId()).map(Customer::customerId)
                        .orElseThrow(() -> new CreditBalanceNotFoundException(creditBalanceId))
                : null;
        return apply.apply(new ApplyCreditCommand(actor.tenantId(), actor.actorId(), actor.role(), customer, creditBalanceId, targetReservationId, amountCents));
    }

    @Override
    public CreditBalanceView credit(PaymentActor actor, UUID creditBalanceId) {
        return view(actor, credits.find(actor.tenantId(), creditBalanceId).orElseThrow(() -> new CreditBalanceNotFoundException(creditBalanceId)));
    }

    @Override
    public CreditApplication application(PaymentActor actor, UUID creditBalanceId, UUID applicationId) {
        return applications.findApplication(actor.tenantId(), applicationId).filter(found -> found.creditBalanceId().equals(creditBalanceId))
                .orElseThrow(() -> new CreditBalanceNotFoundException(creditBalanceId));
    }

    private CreditBalanceView view(PaymentActor actor, CreditBalance credit) {
        UUID customer = reservations.findByTenantAndId(actor.tenantId(), credit.sourceReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(credit.sourceReservationId())).customerId();
        return new CreditBalanceView(credit, customer);
    }
}
