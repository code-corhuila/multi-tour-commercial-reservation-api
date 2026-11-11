package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExtendPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordPaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

// Same mechanism as the payments (norm 5.3.8, AC-11.9): a known key answers the stored result and writes nothing; a key lost in a race
// discards the second write with its transaction. A key belongs to one request: used on another it is a rule violation, never a replay.
public final class IdempotentDeadlineWrites {
    public record Followup(PaymentFollowup followup, boolean created) {}

    public record Extension(DeadlineView view, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final RecordPaymentFollowup followups;
    private final ExtendPaymentDeadline extensions;
    private final GetPaymentDeadline reads;
    private final PaymentFollowupRepository history;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentDeadlineWrites(RecordPaymentFollowup followups, ExtendPaymentDeadline extensions, GetPaymentDeadline reads,
            PaymentFollowupRepository history, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.followups = followups;
        this.extensions = extensions;
        this.reads = reads;
        this.history = history;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Followup followup(RecordFollowupCommand command, String key) {
        UUID tenant = command.actor().tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Followup(replayFollowup(command, known.get()), false);
        Followup done = run(tenant, key, "payment-followup", () -> followups.record(command), PaymentFollowup::followupId, Followup::new);
        if (done.created()) return done;
        return new Followup(replayFollowup(command, stored(tenant, key)), false);
    }

    public Extension extension(ExtendDeadlineCommand command, String key) {
        UUID tenant = command.actor().tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Extension(replayExtension(command, known.get()), false);
        Extension done = run(tenant, key, "payment-deadline-extension", () -> extensions.extend(command), DeadlineView::reservationId,
                Extension::new);
        if (done.created()) return done;
        return new Extension(replayExtension(command, stored(tenant, key)), false);
    }

    private <V, R> R run(UUID tenant, String key, String type, Supplier<V> work, java.util.function.Function<V, UUID> resource,
            java.util.function.BiFunction<V, Boolean, R> result) {
        return transactions.execute(status -> {
            V value = work.get();
            boolean registered = keys.tryRegister(tenant, key, type, resource.apply(value));
            if (!registered) status.setRollbackOnly(); // lost a race with the same key: discard this write
            return result.apply(value, registered);
        });
    }

    private UUID stored(UUID tenant, String key) {
        return keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
    }

    private PaymentFollowup replayFollowup(RecordFollowupCommand command, UUID followupId) {
        return history.find(command.actor().tenantId(), followupId)
                .filter(found -> found.reservationId().equals(command.reservationId()) && command.note() != null
                        && found.note().equals(command.note().strip()))
                .orElseThrow(() -> new BusinessRuleViolation(USED));
    }

    private DeadlineView replayExtension(ExtendDeadlineCommand command, UUID reservationId) {
        if (!reservationId.equals(command.reservationId())) throw new BusinessRuleViolation(USED);
        DeadlineView view = reads.get(command.actor(), reservationId);
        if (command.hours() == null || !command.hours().equals(view.deadline().extensionHours())) throw new BusinessRuleViolation(USED);
        return view;
    }
}
