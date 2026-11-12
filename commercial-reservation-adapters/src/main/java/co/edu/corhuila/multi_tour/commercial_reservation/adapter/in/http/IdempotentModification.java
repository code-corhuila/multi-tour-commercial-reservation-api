package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ModifyReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Norm 5.3.8, AC-12.9: a known key answers the stored record and writes nothing; the key is registered in the same transaction as the
// modification, so a refusal can be asked again, and a key lost in a race discards the second modification with its events. A key belongs
// to one request: used on another reservation, change or reason it is a rule violation, never a replay.
public final class IdempotentModification {
    public record Done(ReservationModificationRecord record, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final ModifyReservation modification;
    private final GetReservationModification reads;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentModification(ModifyReservation modification, GetReservationModification reads, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.modification = modification;
        this.reads = reads;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Done modify(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason, String key) {
        UUID tenant = actor.tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Done(replay(actor, reservationId, request, reason, known.get()), false);
        Done done = transactions.execute(status -> {
            ReservationModificationRecord record = modification.modify(actor, reservationId, request, reason);
            boolean registered = keys.tryRegister(tenant, key, "reservation-modification", record.modificationId());
            if (!registered) status.setRollbackOnly(); // lost a race with the same key: discard this modification and its events
            return new Done(record, registered);
        });
        if (done.created()) return done;
        return new Done(replay(actor, reservationId, request, reason,
                keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"))), false);
    }

    private ReservationModificationRecord replay(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason, UUID stored) {
        ReservationModificationRecord record = reads.get(actor, reservationId, stored);
        if (!record.reservationId().equals(reservationId) || record.changeType() != request.changeType() || reason == null
                || !record.reason().equals(reason.strip())) throw new BusinessRuleViolation(USED);
        return record;
    }
}
