package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ModifyReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;

// Norm 5.3.8, AC-12.9: a known key answers the stored record and writes nothing; the key is registered in the same transaction as the
// modification, so a refusal can be asked again, and a key lost in a race discards the second modification with its events. A key belongs
// to one request: used on another reservation, change or reason it is a rule violation, never a replay.
public final class IdempotentModification {
    public record Done(ReservationModificationRecord record, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final ModifyReservation modification;
    private final GetReservationModification reads;
    private final IdempotentWrites writes;

    public IdempotentModification(ModifyReservation modification, GetReservationModification reads, IdempotentWrites writes) {
        this.modification = modification;
        this.reads = reads;
        this.writes = writes;
    }

    public Done modify(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason, String key) {
        var done = writes.run(actor.tenantId(), key, "reservation-modification", () -> modification.modify(actor, reservationId, request, reason),
                ReservationModificationRecord::modificationId, stored -> replay(actor, reservationId, request, reason, stored));
        return new Done(done.value(), done.created());
    }

    private ReservationModificationRecord replay(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason, UUID stored) {
        ReservationModificationRecord record = reads.get(actor, reservationId, stored);
        if (!record.reservationId().equals(reservationId) || record.changeType() != request.changeType() || reason == null
                || !record.reason().equals(reason.strip())) throw new BusinessRuleViolation(USED);
        return record;
    }
}
