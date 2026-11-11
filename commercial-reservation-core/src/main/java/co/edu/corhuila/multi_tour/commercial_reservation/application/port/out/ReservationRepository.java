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
}
