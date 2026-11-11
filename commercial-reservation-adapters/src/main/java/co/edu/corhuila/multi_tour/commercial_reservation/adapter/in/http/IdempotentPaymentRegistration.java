package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterCashCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisterTransferCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RegisteredPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCashPayment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterTransfer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// Same mechanism as the creation of a reservation: a known key answers the payment already registered and writes nothing; a
// key lost in a race discards the second attempt, its proof and its events with the transaction (AC-09.1, AC-09.2).
public final class IdempotentPaymentRegistration {
    public record Result(Reservation reservation, PaymentAttempt attempt, boolean created) {}

    private final RegisterTransfer transfers;
    private final RegisterCashPayment cash;
    private final ReservationRepository reservations;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentPaymentRegistration(RegisterTransfer transfers, RegisterCashPayment cash, ReservationRepository reservations,
            IdempotencyKeys keys, TransactionTemplate transactions) {
        this.transfers = transfers;
        this.cash = cash;
        this.reservations = reservations;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result transfer(RegisterTransferCommand command, String key) {
        return run(command.tenantId(), command.reservationId(), key, PaymentMethod.TRANSFER, command.amountCents(),
                () -> transfers.register(command));
    }

    public Result cash(RegisterCashCommand command, String key) {
        return run(command.tenantId(), command.reservationId(), key, PaymentMethod.CASH, command.amountCents(),
                () -> cash.register(command));
    }

    private Result run(UUID tenant, UUID reservationId, String key, PaymentMethod method, Long amountCents,
            Supplier<RegisteredPayment> work) {
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return replay(tenant, reservationId, known.get(), method, amountCents);
        Result result = transactions.execute(status -> {
            RegisteredPayment registered = work.get();
            if (keys.tryRegister(tenant, key, "payment", registered.attempt().attemptId())) {
                return new Result(registered.reservation(), registered.attempt(), true);
            }
            status.setRollbackOnly(); // lost a race with the same key: discard this attempt, its proof and its events
            return new Result(registered.reservation(), registered.attempt(), false);
        });
        if (result.created()) return result;
        return replay(tenant, reservationId,
                keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")),
                method, amountCents);
    }

    // A key belongs to one payment of one reservation: used anywhere else, or with another medium or amount, it is a rule violation,
    // not a replay (AC-09.12): answering the first payment would tell the caller it registered what it did not send.
    private Result replay(UUID tenant, UUID reservationId, UUID attemptId, PaymentMethod method, Long amountCents) {
        Reservation reservation = reservations.findByTenantAndAttempt(tenant, attemptId)
                .filter(found -> found.reservationId().equals(reservationId))
                .orElseThrow(() -> new BusinessRuleViolation("the Idempotency-Key was already used by another request"));
        PaymentAttempt attempt = reservation.attempts().stream().filter(found -> found.attemptId().equals(attemptId)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolation("the Idempotency-Key was already used by another request"));
        boolean same = attempt.method() == method && amountCents != null && attempt.amount().amount() == amountCents;
        if (!same) throw new BusinessRuleViolation("the Idempotency-Key was already used with another request");
        return new Result(reservation, attempt, false);
    }
}
