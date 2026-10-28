package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// What this slice reads of a reservation; the reservation itself belongs to HU-COM-007.
public record ReservationSnapshot(UUID tenantId, UUID reservationId, State state, Cents subtotal, Cents paid,
        int appliedDiscounts) {

    public enum State { PENDING_PAYMENT, CONFIRMED, CANCELLED, OTHER }

    // RN-06: only Pendiente de pago or Confirmada.
    public boolean eligible() { return state == State.PENDING_PAYMENT || state == State.CONFIRMED; }
}
