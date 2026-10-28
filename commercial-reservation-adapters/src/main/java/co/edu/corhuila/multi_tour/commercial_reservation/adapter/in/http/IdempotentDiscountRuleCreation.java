package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import org.springframework.transaction.support.TransactionTemplate;

public final class IdempotentDiscountRuleCreation {
    public record Result(DiscountRule rule, boolean created) {}
    private final CreateDiscountRule create;
    private final GetDiscountRule get;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentDiscountRuleCreation(CreateDiscountRule create, GetDiscountRule get, IdempotencyKeys keys,
            TransactionTemplate transactions) {
        this.create = create;
        this.get = get;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result create(DiscountRuleActor actor, CreateDiscountRule.Command command, String key) {
        // A known key answers the original rule: nothing is written and no event is emitted.
        var known = keys.findResourceId(actor.tenantId(), key);
        if (known.isPresent()) return new Result(get.get(actor, known.get()), false);
        Result result = transactions.execute(status -> {
            DiscountRule rule = create.create(actor, command);
            if (keys.tryRegister(actor.tenantId(), key, "discount_rule", rule.ruleId())) return new Result(rule, true);
            status.setRollbackOnly(); // lost a race with the same key: discard this insert and its event
            return new Result(rule, false);
        });
        if (result.created()) return result;
        var id = keys.findResourceId(actor.tenantId(), key)
                .orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Result(get.get(actor, id), false);
    }
}
