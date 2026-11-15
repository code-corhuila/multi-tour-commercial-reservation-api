package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;

// A known key answers the original rule: nothing is written and no event is emitted; the rule is IdempotentWrites'.
public final class IdempotentDiscountRuleCreation {
    public record Result(DiscountRule rule, boolean created) {}
    private final CreateDiscountRule create;
    private final GetDiscountRule get;
    private final IdempotentWrites writes;

    public IdempotentDiscountRuleCreation(CreateDiscountRule create, GetDiscountRule get, IdempotentWrites writes) {
        this.create = create;
        this.get = get;
        this.writes = writes;
    }

    public Result create(DiscountRuleActor actor, CreateDiscountRule.Command command, String key) {
        var done = writes.run(actor.tenantId(), key, "discount_rule", () -> create.create(actor, command), DiscountRule::ruleId, id -> get.get(actor, id));
        return new Result(done.value(), done.created());
    }
}
