package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Opens the pending treatment when the new subtotal is below what is already paid (RN-13, plan D-06).
public interface ExcessTreatment {
    void open(UUID tenantId, UUID reservationId, Cents excess);
}
