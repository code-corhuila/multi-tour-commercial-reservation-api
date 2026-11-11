package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageRefundDecisions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadRefunds;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// AC-16.17, RNF-06: the key is registered last, in the same transaction as the decision or the refund and its event, so a refusal can be asked again with the
// same key. A repeat answers the stored record (200, the first time 201 for an authorization); a key lost in a race discards the second write and answers the
// first. A key belongs to one request: the stored record must be the one of the path.
public class IdempotentRefundWrites {
    public record Outcome<T>(T value, boolean created) {}

    private final ManageRefundDecisions manage;
    private final ReadRefunds read;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentRefundWrites(ManageRefundDecisions manage, ReadRefunds read, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.manage = manage;
        this.read = read;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Outcome<FinancialDecision> record(PaymentActor actor, String key, UUID decisionId) {
        return once(actor, key, "refund-decision", decisionId, () -> manage.recordRefundDecision(actor, decisionId), FinancialDecision::id,
                id -> read.decision(actor, id), found -> found.id().equals(decisionId));
    }

    public Outcome<FinancialDecision> reject(PaymentActor actor, String key, UUID decisionId, String justification) {
        return once(actor, key, "refund-rejection", decisionId, () -> manage.reject(actor, decisionId, justification), FinancialDecision::id,
                id -> read.decision(actor, id), found -> found.id().equals(decisionId));
    }

    public Outcome<RefundAuthorization> authorize(PaymentActor actor, String key, UUID decisionId, String reason) {
        return once(actor, key, "refund-authorization", decisionId, () -> manage.authorize(actor, decisionId, reason), RefundAuthorization::id,
                id -> read.refund(actor, id), found -> decisionId.equals(found.financialDecisionId()));
    }

    private <T> Outcome<T> once(PaymentActor actor, String key, String type, UUID decisionId, Supplier<T> act, Function<T, UUID> idOf, Function<UUID, T> load,
            java.util.function.Predicate<T> mine) {
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

    private static <T> Outcome<T> replay(T stored, java.util.function.Predicate<T> mine) {
        if (!mine.test(stored)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return new Outcome<>(stored, false);
    }
}
