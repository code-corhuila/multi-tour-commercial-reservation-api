package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.UUID;

// AC-16.17, RNF-06: the key is registered last, in the same transaction as the refund and its event, so a refusal can be asked again with the same key. A
// repeat answers the stored refund (200); a key lost in a race discards the second refund and answers the first. A key belongs to one request.
public class IdempotentRefundDetermination {
    public record Outcome(RefundAuthorization refund, boolean created) {}

    private final DetermineRefund determine;
    private final ExecuteRefund reads;
    private final IdempotentWrites writes;

    public IdempotentRefundDetermination(DetermineRefund determine, ExecuteRefund reads, IdempotentWrites writes) {
        this.determine = determine;
        this.reads = reads;
        this.writes = writes;
    }

    public Outcome determine(UUID tenantId, String key, UUID reservationId, RefundOrigin origin, Long amountCents) {
        var done = writes.run(tenantId, key, "refund-determination", () -> determine.determine(tenantId, reservationId, origin, amountCents),
                RefundAuthorization::id, stored -> replay(tenantId, stored, reservationId, origin));
        return new Outcome(done.value(), done.created());
    }

    private RefundAuthorization replay(UUID tenantId, UUID stored, UUID reservationId, RefundOrigin origin) {
        RefundAuthorization refund = reads.find(tenantId, stored);
        if (!refund.reservationId().equals(reservationId) || refund.origin() != origin)
            throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return refund;
    }
}
