package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CancelReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Norm 5.3.8, AC-13.8, RNF-06: a known key answers the stored record and writes nothing; the key is registered in the same transaction as
// the cancellation, so a refusal can be asked again, and a key lost in a race discards the second cancellation with its events. A key
// belongs to one request: used on another reservation or with another reason it is a rule violation, never a replay.
public final class IdempotentCancellation {
    public record Done(CancellationRecord record, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final CancelReservation cancellation;
    private final GetCancellationRecord reads;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentCancellation(CancelReservation cancellation, GetCancellationRecord reads, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.cancellation = cancellation;
        this.reads = reads;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Done cancel(CancelReservationCommand command, String key) {
        UUID tenant = command.actor().tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Done(replay(command, known.get()), false);
        Done done = transactions.execute(status -> {
            CancellationRecord record = cancellation.cancel(command);
            boolean registered = keys.tryRegister(tenant, key, "reservation-cancellation", record.reservationId());
            if (!registered) status.setRollbackOnly(); // lost a race with the same key: discard this cancellation and its events
            return new Done(record, registered);
        });
        if (done.created()) return done;
        return new Done(replay(command, keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"))), false);
    }

    private CancellationRecord replay(CancelReservationCommand command, UUID stored) {
        if (!stored.equals(command.reservationId())) throw new BusinessRuleViolation(USED);
        CancellationRecord record = reads.get(command.actor(), stored);
        if (command.reason() == null || !record.reason().equals(command.reason().strip())) throw new BusinessRuleViolation(USED);
        return record;
    }
}
