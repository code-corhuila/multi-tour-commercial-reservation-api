package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCreditBalances;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// AC-17.15, RNF-06: the key is registered last, in the same transaction as the credit or the application and its effects, so a refusal can be asked again with the
// same key. A repeat answers the stored record (200, the first time 201 for an application); a key lost in a race discards the second write and answers the first.
// A key belongs to one request: the stored record must be the one of the path.
public class IdempotentCreditWrites {
    public record Outcome<T>(T value, boolean created) {}

    private final ManageCreditBalances credits;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentCreditWrites(ManageCreditBalances credits, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.credits = credits;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Outcome<CreditBalanceView> recordDecision(PaymentActor actor, String key, UUID decisionId) {
        return once(actor, key, "credit-decision", () -> credits.recordCreditDecision(actor, decisionId), view -> view.credit().id(),
                id -> credits.credit(actor, id), found -> decisionId.equals(found.credit().sourceDecisionId()));
    }

    public Outcome<CreditApplication> apply(PaymentActor actor, String key, UUID creditId, UUID target, long amountCents) {
        return once(actor, key, "credit-application", () -> credits.apply(actor, creditId, target, amountCents), CreditApplication::id,
                id -> credits.application(actor, creditId, id), found -> found.creditBalanceId().equals(creditId));
    }

    private <T> Outcome<T> once(PaymentActor actor, String key, String type, Supplier<T> act, Function<T, UUID> idOf, Function<UUID, T> load, Predicate<T> mine) {
        UUID tenant = actor.tenantId();
        Optional<UUID> known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return replay(load.apply(known.get()), mine);
        Object[] made = new Object[1];
        Boolean lost = transactions.execute(status -> {
            @SuppressWarnings("unchecked") T value = (T) (made[0] = act.get());
            if (!keys.tryRegister(tenant, key, type, idOf.apply(value))) {
                status.setRollbackOnly();
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(lost)) {
            return replay(load.apply(keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"))), mine);
        }
        @SuppressWarnings("unchecked") T value = (T) made[0];
        return new Outcome<>(value, true);
    }

    private static <T> Outcome<T> replay(T stored, Predicate<T> mine) {
        if (!mine.test(stored)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return new Outcome<>(stored, false);
    }
}
