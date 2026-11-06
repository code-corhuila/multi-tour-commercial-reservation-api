package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// No delete operation on purpose: a reservation is never deleted, it changes state. The reads join with T-09 and T-10.
public interface ReservationRepository {

    void save(Reservation reservation);

    // Scoped to the tenant: another tenant's reservation is empty, like a missing one. Until the JDBC adapter overrides it
    // the payment use cases are refused instead of silently passing.
    default java.util.Optional<Reservation> findByTenantAndId(java.util.UUID tenantId, java.util.UUID reservationId) {
        throw new UnsupportedOperationException("the reservation lookup needs the adapter override");
    }

    // The reservation that holds a payment attempt, for the review routes that name only the attempt; empty for another tenant.
    default java.util.Optional<Reservation> findByTenantAndAttempt(java.util.UUID tenantId, java.util.UUID attemptId) {
        throw new UnsupportedOperationException("the attempt lookup needs the adapter override");
    }
    // The same lookup under a row lock (SELECT ... FOR UPDATE) for the writes that decide on the state they read: the expiry and the
    // extension (HU-COM-011 RN-06, RNF-06). The adapter overrides it; a double without a lock may keep this default.
    default java.util.Optional<Reservation> lockByTenantAndId(java.util.UUID tenantId, java.util.UUID reservationId) {
        return findByTenantAndId(tenantId, reservationId);
    }

    // The reservation that holds a payment attempt, under the same row lock, for the decision of a proof (HU-COM-010 RNF-06): two decisions
    // on one proof, or on two proofs of one reservation, queue on the row and the second reads what the first committed. Run it inside the
    // transaction of the use case; the attempt state to decide on is the one of the returned reservation, never one read before the lock.
    default java.util.Optional<Reservation> lockByTenantAndAttempt(java.util.UUID tenantId, java.util.UUID attemptId) {
        return findByTenantAndAttempt(tenantId, attemptId).flatMap(found -> lockByTenantAndId(tenantId, found.reservationId()));
    }
}
