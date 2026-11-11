package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// AC-16.17, RNF-06: the key is registered last, in the same transaction as the refund and its event, so a refusal can be asked again with the same key. A
// repeat answers the stored refund (200); a key lost in a race discards the second refund and answers the first. A key belongs to one request.
public class IdempotentRefundDetermination {
    public record Outcome(RefundAuthorization refund, boolean created) {}

    private final DetermineRefund determine;
    private final ExecuteRefund reads;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentRefundDetermination(DetermineRefund determine, ExecuteRefund reads, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.determine = determine;
        this.reads = reads;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Outcome determine(UUID tenantId, String key, UUID reservationId, RefundOrigin origin, Long amountCents) {
        Optional<UUID> known = keys.findResourceId(tenantId, key);
        if (known.isPresent()) return replay(tenantId, known.get(), reservationId, origin);
        RefundAuthorization[] made = new RefundAuthorization[1];
        Boolean lost = transactions.execute(status -> {
            made[0] = determine.determine(tenantId, reservationId, origin, amountCents);
            if (!keys.tryRegister(tenantId, key, "refund-determination", made[0].id())) {
                status.setRollbackOnly();
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(lost)) {
            return replay(tenantId, keys.findResourceId(tenantId, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")),
                    reservationId, origin);
        }
        return new Outcome(made[0], true);
    }

    private Outcome replay(UUID tenantId, UUID stored, UUID reservationId, RefundOrigin origin) {
        RefundAuthorization refund = reads.find(tenantId, stored);
        if (!refund.reservationId().equals(reservationId) || refund.origin() != origin)
            throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        return new Outcome(refund, false);
    }
}
