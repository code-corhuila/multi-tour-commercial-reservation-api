package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExpireReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// AC-11.9, RNF-06: only a real expiry registers its key, in the same transaction as the cancellation, so a refusal such as NOT_DUE can be
// asked again later with the same key. A repeat of an expiry that was done answers the stored result (the reservation is already cancelled
// for the expiry); a key lost in a race discards the second cancellation. A key belongs to one reservation.
public final class IdempotentExpiry {
    private final ExpireReservation expiry;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentExpiry(ExpireReservation expiry, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.expiry = expiry;
        this.keys = keys;
        this.transactions = transactions;
    }

    public ExpiryResult expire(UUID tenantId, UUID reservationId, String key) {
        var known = keys.findResourceId(tenantId, key);
        if (known.isPresent()) return replay(tenantId, reservationId, known.get());
        ExpiryResult[] done = new ExpiryResult[1];
        Boolean lost = transactions.execute(status -> {
            done[0] = expiry.expire(tenantId, reservationId);
            if (done[0].expired() && !keys.tryRegister(tenantId, key, "reservation-expiry", reservationId)) {
                status.setRollbackOnly(); // lost a race with the same key: discard this cancellation and its events
                return true;
            }
            return false;
        });
        if (!Boolean.TRUE.equals(lost)) return done[0];
        return replay(tenantId, reservationId, keys.findResourceId(tenantId, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")));
    }

    private ExpiryResult replay(UUID tenantId, UUID reservationId, UUID stored) {
        if (!stored.equals(reservationId)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        ExpiryResult now = expiry.expire(tenantId, reservationId);
        return now.reason() == ExpiryVerdict.ALREADY_CANCELLED
                ? new ExpiryResult(reservationId, true, null, CancellationCause.PAYMENT_EXPIRY, ReservationStatus.CANCELLED) : now;
    }
}
