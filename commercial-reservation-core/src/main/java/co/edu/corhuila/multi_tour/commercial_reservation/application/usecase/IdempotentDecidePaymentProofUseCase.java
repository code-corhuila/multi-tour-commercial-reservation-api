package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DecidePaymentProofWithKey;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

// The permission comes first, so nobody learns a stored decision from a key. The key is registered first inside the transaction of the
// decision: a concurrent request with the same key waits on it, finds it taken and answers the replay; a refused decision rolls the key back.
// A replay needs the same proof and the same body as the stored decision (state, verified amount and reason); anything else is refused.
public class IdempotentDecidePaymentProofUseCase implements DecidePaymentProofWithKey {
    static final String RESOURCE_TYPE = "payment-proof-decision";
    private static final String REUSED = "the Idempotency-Key was already used by another request";

    // Thrown inside the transaction when the key is taken, so the transaction ends without writing anything.
    private static final class KeyTaken extends RuntimeException {
        KeyTaken() { super(null, null, false, false); }
    }

    private final DecidePaymentProof decisions;
    private final ReservationRepository reservations;
    private final IdempotencyStore keys;
    private final PaymentTransaction transaction;

    public IdempotentDecidePaymentProofUseCase(DecidePaymentProof decisions, ReservationRepository reservations, IdempotencyStore keys,
            PaymentTransaction transaction) {
        this.decisions = decisions;
        this.reservations = reservations;
        this.keys = keys;
        this.transaction = transaction;
    }

    @Override
    public DecidedProof approve(ApproveProofCommand command, String key) {
        PaymentProofAuthorization.requireDecide(command.actor());
        String reason = normalized(command.reason());
        return run(command.actor().tenantId(), command.attemptId(), key, () -> decisions.approve(command),
                stored -> stored.validationStatus() == ValidationStatus.APPROVED && command.verifiedAmountCents() != null
                        && stored.verifiedAmount() != null && stored.verifiedAmount().amount() == command.verifiedAmountCents()
                        && Objects.equals(stored.decisionReason(), reason));
    }

    @Override
    public DecidedProof reject(RejectProofCommand command, String key) {
        PaymentProofAuthorization.requireDecide(command.actor());
        String reason = normalized(command.reason());
        return run(command.actor().tenantId(), command.attemptId(), key, () -> decisions.reject(command),
                stored -> stored.validationStatus() == ValidationStatus.REJECTED && Objects.equals(stored.decisionReason(), reason));
    }

    private DecidedProof run(UUID tenant, UUID attemptId, String key, Supplier<DecidedProof> work, Predicate<PaymentAttempt> sameBody) {
        Optional<UUID> known = keys.find(tenant, key);
        if (known.isPresent()) return replay(tenant, attemptId, known.get(), sameBody);
        try {
            return transaction.inTransaction(() -> {
                if (!keys.register(tenant, key, RESOURCE_TYPE, attemptId)) throw new KeyTaken();
                return work.get();
            });
        } catch (KeyTaken lost) {
            UUID winner = keys.find(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
            return replay(tenant, attemptId, winner, sameBody);
        }
    }

    private DecidedProof replay(UUID tenant, UUID attemptId, UUID stored, Predicate<PaymentAttempt> sameBody) {
        if (!stored.equals(attemptId)) throw new BusinessRuleViolation(REUSED);
        var reservation = reservations.findByTenantAndAttempt(tenant, attemptId).orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        PaymentAttempt attempt = reservation.attempts().stream().filter(found -> found.attemptId().equals(attemptId)).findFirst()
                .orElseThrow(() -> new PaymentProofNotFoundException(attemptId));
        if (!sameBody.test(attempt)) throw new BusinessRuleViolation(REUSED);
        return new DecidedProof(reservation, attempt, attempt.appliedAmount() == null ? new Cents(0) : attempt.appliedAmount(),
                attempt.excessAmount() == null ? new Cents(0) : attempt.excessAmount());
    }

    private static String normalized(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
