package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonTimes;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonTimes.Times;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The use case is not transactional: this wrapper gives the idempotent add one transaction and makes a disable
// lock the tenant catalogue first, so "the last enabled reason stays" holds under concurrency.
public final class TransactionalAuthorizableReasons {
    public record Item(AuthorizableReason reason, Times times) {}
    public record Added(Item item, boolean created) {}
    private final ManageAuthorizableReasons reasons;
    private final AuthorizableReasonTimes times;
    private final IdempotentWrites writes;
    private final RequestTransaction transaction;

    public TransactionalAuthorizableReasons(ManageAuthorizableReasons reasons, AuthorizableReasonTimes times,
            IdempotentWrites writes, RequestTransaction transaction) {
        this.reasons = reasons;
        this.times = times;
        this.writes = writes;
        this.transaction = transaction;
    }

    public List<Item> list(DiscountRuleActor actor) {
        Map<UUID, Times> byId = times.timesByTenant(actor.tenantId());
        return reasons.list(actor).stream().map(reason -> new Item(reason, byId.get(reason.reasonId()))).toList();
    }

    public Added add(DiscountRuleActor actor, String text, String key) {
        // A known key answers the original reason: nothing is written.
        var done = writes.run(actor.tenantId(), key, "authorizable_reason", () -> find(actor, reasons.add(actor, text).reasonId()),
                item -> item.reason().reasonId(), id -> find(actor, id));
        return new Added(done.value(), done.created());
    }

    public Item disable(DiscountRuleActor actor, UUID reasonId) {
        AuthorizableReason disabled = transaction.inTransaction(() -> {
            times.lockCatalogue(actor.tenantId());
            return reasons.disable(actor, reasonId);
        });
        return find(actor, disabled.reasonId());
    }

    private Item find(DiscountRuleActor actor, UUID reasonId) {
        return list(actor).stream().filter(item -> item.reason().reasonId().equals(reasonId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("reason not found after it was written"));
    }
}
