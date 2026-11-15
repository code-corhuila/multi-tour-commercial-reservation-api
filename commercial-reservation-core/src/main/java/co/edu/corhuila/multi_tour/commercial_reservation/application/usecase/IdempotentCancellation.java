package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CancelReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;

// Norm 5.3.8, AC-13.8, RNF-06: a known key answers the stored record and writes nothing; the key is registered in the same transaction as
// the cancellation, so a refusal can be asked again, and a key lost in a race discards the second cancellation with its events. A key
// belongs to one request: used on another reservation or with another reason it is a rule violation, never a replay.
public final class IdempotentCancellation {
    public record Done(CancellationRecord record, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final CancelReservation cancellation;
    private final GetCancellationRecord reads;
    private final IdempotentWrites writes;

    public IdempotentCancellation(CancelReservation cancellation, GetCancellationRecord reads, IdempotentWrites writes) {
        this.cancellation = cancellation;
        this.reads = reads;
        this.writes = writes;
    }

    public Done cancel(CancelReservationCommand command, String key) {
        var done = writes.run(command.actor().tenantId(), key, "reservation-cancellation", () -> cancellation.cancel(command), CancellationRecord::reservationId,
                stored -> replay(command, stored));
        return new Done(done.value(), done.created());
    }

    private CancellationRecord replay(CancelReservationCommand command, UUID stored) {
        if (!stored.equals(command.reservationId())) throw new BusinessRuleViolation(USED);
        CancellationRecord record = reads.get(command.actor(), stored);
        if (command.reason() == null || !record.reason().equals(command.reason().strip())) throw new BusinessRuleViolation(USED);
        return record;
    }
}
