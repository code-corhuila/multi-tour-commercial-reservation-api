package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import java.util.Optional;
import java.util.UUID;

// Insert only: the record is immutable and nothing is deleted.
public interface AdditionalDiscountRepository {
    void save(AdditionalDiscount discount);

    boolean existsByReservation(UUID tenantId, UUID reservationId);

    // The stored record of one reservation, for the answer of an idempotent repeat; a foreign tenant gets the same empty answer.
    Optional<AdditionalDiscount> findByReservation(UUID tenantId, UUID reservationId);
}
