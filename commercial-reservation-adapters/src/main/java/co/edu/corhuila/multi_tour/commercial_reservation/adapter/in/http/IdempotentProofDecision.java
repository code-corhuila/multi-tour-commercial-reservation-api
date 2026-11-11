package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// A repeat with the same Idempotency-Key answers the stored decision and writes nothing (contract of approve and reject); a key lost in
// a race discards the second decision with its events. A key belongs to one proof: used on another it is a rule violation.
public final class IdempotentProofDecision {
    private final DecidePaymentProof decisions;
    private final ReservationRepository reservations;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentProofDecision(DecidePaymentProof decisions, ReservationRepository reservations, IdempotencyKeys keys,
            TransactionTemplate transactions) {
        this.decisions = decisions;
        this.reservations = reservations;
        this.keys = keys;
        this.transactions = transactions;
    }

    public DecidedProof approve(ApproveProofCommand command, String key) {
        return run(command.actor().tenantId(), command.attemptId(), key, () -> decisions.approve(command));
    }

    public DecidedProof reject(RejectProofCommand command, String key) {
        return run(command.actor().tenantId(), command.attemptId(), key, () -> decisions.reject(command));
    }

    private DecidedProof run(UUID tenant, UUID attemptId, String key, Supplier<DecidedProof> work) {
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return replay(tenant, attemptId, known.get());
        DecidedProof[] decided = new DecidedProof[1];
        Boolean registered = transactions.execute(status -> {
            decided[0] = work.get();
            if (keys.tryRegister(tenant, key, "payment-proof-decision", attemptId)) return true;
            status.setRollbackOnly(); // lost a race with the same key: discard this decision and its events
            return false;
        });
        if (Boolean.TRUE.equals(registered)) return decided[0];
        return replay(tenant, attemptId, keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")));
    }

    private DecidedProof replay(UUID tenant, UUID attemptId, UUID stored) {
        if (!stored.equals(attemptId)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        var reservation = reservations.findByTenantAndAttempt(tenant, attemptId).orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        PaymentAttempt attempt = reservation.attempts().stream().filter(found -> found.attemptId().equals(attemptId)).findFirst()
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        return new DecidedProof(reservation, attempt, attempt.appliedAmount() == null ? new Cents(0) : attempt.appliedAmount(),
                attempt.excessAmount() == null ? new Cents(0) : attempt.excessAmount());
    }
}
