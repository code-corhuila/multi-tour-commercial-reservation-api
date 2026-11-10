package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplyCreditCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceApplications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication.AppliedBy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalanceApplied;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

// HU-COM-017 RN-04 to RN-07; plan D-04, declared debt DD-2: applying a credit is a validated payment with no proof, no attempt and no cash movement. The checks come in the
// order of the plan (role, credit of the tenant, ownership, tenant and customer of the target, status, departure start, pending balance, ceilings) and nothing is written
// before they pass; the first payment holds the seat in the same transaction, so a full departure applies nothing. The application, the credit, the reservation and its events
// commit together.
public class ApplyCreditBalanceUseCase {

    private final CreditBalanceRepository credits;
    private final CreditBalanceApplications applications;
    private final ReservationRepository reservations;
    private final PaymentPreconditions preconditions;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final IdGenerator ids;

    public ApplyCreditBalanceUseCase(CreditBalanceRepository credits, CreditBalanceApplications applications, ReservationRepository reservations,
            DepartureReader departures, PaymentDeadlineReader deadlines, PaymentConditionReader conditions, SalidaCapacityHolder holder, PaymentEventOutbox outbox,
            PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        this.credits = credits;
        this.applications = applications;
        this.reservations = reservations;
        this.preconditions = new PaymentPreconditions(reservations, departures, deadlines, (tenant, service, type, method) -> true, conditions, holder, clock);
        this.outbox = outbox;
        this.transaction = transaction;
        this.ids = ids;
    }

    public CreditApplication apply(ApplyCreditCommand command) {
        PaymentAuthorization.requireRegister(new PaymentActor(command.tenantId(), command.actorId(), command.role(), Set.of()));
        Cents amount = Cents.of("amountCents", command.amountCents());
        return transaction.inTransaction(() -> {
            CreditBalance credit = credits.find(command.tenantId(), command.creditBalanceId())
                    .orElseThrow(() -> new CreditBalanceNotFoundException(command.creditBalanceId()));
            UUID holder = reservations.findByTenantAndId(command.tenantId(), credit.sourceReservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(credit.sourceReservationId())).customerId();
            if (command.role() == Role.FINAL_CUSTOMER && !holder.equals(command.actorCustomerId())) throw new PaymentAccessDeniedException();
            Reservation target = preconditions.requireOpenForCredit(command.tenantId(), command.targetReservationId());
            if (!target.customerId().equals(holder)) throw new BusinessRuleViolation("a credit balance is applied only to a reservation of its own customer");
            if (amount.amount() > target.pendingBalance().amount()) throw new BusinessRuleViolation("the amount is above the pending balance of the reservation");
            CreditBalance updated = credit.withApplied(amount);
            preconditions.holdFirstSeat(target);
            target.applyCreditBalance(amount, preconditions.minimumFor(target), command.actorId(), preconditions.now());
            CreditApplication application = new CreditApplication(ids.newId(), credit.id(), command.tenantId(), target.reservationId(), amount, preconditions.now(),
                    command.actorId(), command.role() == Role.FINAL_CUSTOMER ? AppliedBy.CUSTOMER : AppliedBy.STAFF);
            applications.save(application);
            credits.save(updated);
            reservations.save(target);
            outbox.append(new CreditBalanceApplied(command.tenantId(), target.reservationId(), application.appliedAt(), credit.id(), credit.sourceReservationId(), amount,
                    application.kind(), command.actorId()));
            target.pullEvents().forEach(outbox::append);
            return application;
        });
    }
}
