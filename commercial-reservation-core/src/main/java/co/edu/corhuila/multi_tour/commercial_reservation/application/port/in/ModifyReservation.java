package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import java.util.UUID;

// HU-COM-012 AC-12.1 to AC-12.5, AC-12.11: changes the party, the companions or the departure of a reservation before execution.
public interface ModifyReservation {
    ModificationOutcome modify(PaymentActor actor, UUID reservationId, ModificationRequest request, String reason);
}
