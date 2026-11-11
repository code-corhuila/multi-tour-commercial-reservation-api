package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExpiryResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExpireReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExpiryVerdict;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;

// AC-11.9, RNF-06: only a real expiry registers its key, in the same transaction as the cancellation, so a refusal such as NOT_DUE can be
// asked again later with the same key. A repeat of an expiry that was done answers the stored result (the reservation is already cancelled
// for the expiry); a key lost in a race discards the second cancellation. A key belongs to one reservation.
public final class IdempotentExpiry {
    private final ExpireReservation expiry;
    private final IdempotentWrites writes;

    public IdempotentExpiry(ExpireReservation expiry, IdempotentWrites writes) {
        this.expiry = expiry;
        this.writes = writes;
    }

    public ExpiryResult expire(UUID tenantId, UUID reservationId, String key) {
        return writes.runWhen(tenantId, key, "reservation-expiry", () -> expiry.expire(tenantId, reservationId), ExpiryResult::expired,
                done -> reservationId, stored -> replay(tenantId, reservationId, stored)).value();
    }

    private ExpiryResult replay(UUID tenantId, UUID reservationId, UUID stored) {
        if (!stored.equals(reservationId)) throw new BusinessRuleViolation("the Idempotency-Key was already used by another request");
        ExpiryResult now = expiry.expire(tenantId, reservationId);
        return now.reason() == ExpiryVerdict.ALREADY_CANCELLED
                ? new ExpiryResult(reservationId, true, null, CancellationCause.PAYMENT_EXPIRY, ReservationStatus.CANCELLED) : now;
    }
}
