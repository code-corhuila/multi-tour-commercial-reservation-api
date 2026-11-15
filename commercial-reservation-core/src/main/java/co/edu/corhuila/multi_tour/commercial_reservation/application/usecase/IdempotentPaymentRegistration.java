package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

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

// Same mechanism as the creation of a reservation: a known key answers the payment already registered and writes nothing; a
// key lost in a race discards the second attempt, its proof and its events with the transaction (AC-09.1, AC-09.2).
public final class IdempotentPaymentRegistration {
    public record Result(Reservation reservation, PaymentAttempt attempt, boolean created) {}

    private final RegisterTransfer transfers;
    private final RegisterCashPayment cash;
    private final ReservationRepository reservations;
    private final IdempotentWrites writes;

    public IdempotentPaymentRegistration(RegisterTransfer transfers, RegisterCashPayment cash, ReservationRepository reservations,
            IdempotentWrites writes) {
        this.transfers = transfers;
        this.cash = cash;
        this.reservations = reservations;
        this.writes = writes;
    }

    public Result transfer(RegisterTransferCommand command, String key) {
        return run(command.tenantId(), command.reservationId(), key, PaymentMethod.TRANSFER, command.amountCents(),
                () -> transfers.register(command));
    }

    public Result cash(RegisterCashCommand command, String key) {
        return run(command.tenantId(), command.reservationId(), key, PaymentMethod.CASH, command.amountCents(),
                () -> cash.register(command.withKey(key)));
    }

    private Result run(UUID tenant, UUID reservationId, String key, PaymentMethod method, Long amountCents,
            Supplier<RegisteredPayment> work) {
        var done = writes.run(tenant, key, "payment", work, registered -> registered.attempt().attemptId(),
                id -> {
                    Result stored = replay(tenant, reservationId, id, method, amountCents);
                    return new RegisteredPayment(stored.reservation(), stored.attempt());
                });
        return new Result(done.value().reservation(), done.value().attempt(), done.created());
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
