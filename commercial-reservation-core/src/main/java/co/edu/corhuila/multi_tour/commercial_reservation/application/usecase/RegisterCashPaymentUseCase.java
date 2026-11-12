package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCashPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashReceipt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingPaymentDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

// Cash through the saga register-payment (ADR-004 D3, plan D-07): the body, the same preconditions as a transfer, the open
// cash session, then the income of Cash first and the validated payment of Reservations second. If the seat hold of the
// second step fails the income is reversed and nothing is stored. The call between services is OPEN (DP-02).
public class RegisterCashPaymentUseCase implements RegisterCashPayment {

    private final PaymentPreconditions preconditions;
    private final ReservationRepository reservations;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final CashSessionStatus session;
    private final CashIncomeRecorder income;
    private final CreditBalanceRecorder credit;
    private final IdGenerator ids;

    public RegisterCashPaymentUseCase(ReservationRepository reservations, DepartureReader departures, PaymentDeadlineReader deadlines,
            EnabledPaymentOptions options, PaymentConditionReader conditions, SalidaCapacityHolder holder, PaymentEventOutbox outbox,
            PaymentTransaction transaction, CashSessionStatus session, CashIncomeRecorder income, CreditBalanceRecorder credit,
            IdGenerator ids, Clock clock) {
        this.preconditions = new PaymentPreconditions(reservations, departures, deadlines, options, conditions, holder, clock);
        this.reservations = reservations;
        this.outbox = outbox;
        this.transaction = transaction;
        this.session = session;
        this.income = income;
        this.credit = credit;
        this.ids = ids;
    }

    @Override
    public RegisteredPayment register(RegisterCashCommand command) {
        if (command.amountCents() == null) throw new MissingPaymentDataException(List.of("amountCents"));
        Cents amount = Cents.of("amountCents", command.amountCents());
        return transaction.inTransaction(() -> {
            Reservation reservation = preconditions.requireOpenFor(command.tenantId(), command.reservationId(), PaymentMethod.CASH);
            if (!session.isOpen(command.tenantId())) throw new BusinessRuleViolation("there is no open cash session");
            Cents minimum = preconditions.minimumFor(reservation);
            CashReceipt receipt = reservation.registerCash(ids.newId(), amount, minimum, command.actorId(), preconditions.now());
            // Stable per intention: a retry of the same request (same Idempotency-Key and amount) replays the movement in the cash.
            String intent = (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                    ? receipt.attempt().attemptId().toString() : command.idempotencyKey()) + ":" + amount.amount();
            UUID movement = income.record(command.tenantId(), reservation.reservationId(), intent, amount, command.actorId());
            try {
                preconditions.holdFirstSeat(reservation);
                reservations.save(reservation);
                reservation.pullEvents().forEach(outbox::append);
                if (receipt.excess().amount() > 0) {
                    credit.recordExcess(command.tenantId(), reservation.reservationId(), receipt.attempt().attemptId(), receipt.excess());
                }
            } catch (RuntimeException failure) {
                // Never money in the cash without a reservation: the income is reversed whatever the failure was. If the reversal
                // fails too, the original error is kept and the failure is attached (the adapter logs it for the reconciliation).
                try {
                    income.reverse(command.tenantId(), movement);
                } catch (RuntimeException reversalFailure) {
                    failure.addSuppressed(reversalFailure);
                }
                throw failure;
            }
            return new RegisteredPayment(reservation, receipt.attempt());
        });
    }
}
