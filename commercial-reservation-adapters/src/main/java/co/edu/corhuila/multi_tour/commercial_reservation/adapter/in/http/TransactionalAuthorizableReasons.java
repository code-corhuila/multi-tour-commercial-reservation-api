package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.AuthorizableReasonTimes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.AuthorizableReasonTimes.Times;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// The use case is not transactional: this wrapper gives the idempotent add one transaction and makes a disable
// lock the tenant catalogue first, so "the last enabled reason stays" holds under concurrency.
public final class TransactionalAuthorizableReasons {
    public record Item(AuthorizableReason reason, Times times) {}
    public record Added(Item item, boolean created) {}
    private final ManageAuthorizableReasons reasons;
    private final AuthorizableReasonTimes times;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public TransactionalAuthorizableReasons(ManageAuthorizableReasons reasons, AuthorizableReasonTimes times,
            IdempotencyKeys keys, TransactionTemplate transactions) {
        this.reasons = reasons;
        this.times = times;
        this.keys = keys;
        this.transactions = transactions;
    }

    public List<Item> list(DiscountRuleActor actor) {
        Map<UUID, Times> byId = times.timesByTenant(actor.tenantId());
        return reasons.list(actor).stream().map(reason -> new Item(reason, byId.get(reason.reasonId()))).toList();
    }

    public Added add(DiscountRuleActor actor, String text, String key) {
        // A known key answers the original reason: nothing is written.
        var known = keys.findResourceId(actor.tenantId(), key);
        if (known.isPresent()) return new Added(find(actor, known.get()), false);
        AuthorizableReason added = transactions.execute(status -> {
            AuthorizableReason reason = reasons.add(actor, text);
            if (keys.tryRegister(actor.tenantId(), key, "authorizable_reason", reason.reasonId())) return reason;
            status.setRollbackOnly(); // lost a race with the same key: discard this insert
            return null;
        });
        if (added != null) return new Added(find(actor, added.reasonId()), true);
        var id = keys.findResourceId(actor.tenantId(), key)
                .orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Added(find(actor, id), false);
    }

    public Item disable(DiscountRuleActor actor, UUID reasonId) {
        AuthorizableReason disabled = transactions.execute(status -> {
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
