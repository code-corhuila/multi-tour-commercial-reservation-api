package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Step 1 of the saga register-payment (ADR-004 D3): Cash records the income first, forwarding the user who registered the
// cash, and reverses it if the Reservations step fails. Who calls whom is OPEN (DP-02); this service never writes to Cash.
public interface CashIncomeRecorder {

    UUID record(UUID tenantId, UUID reservationId, Cents amount, UUID registeredBy);

    void reverse(UUID tenantId, UUID movementId);
}
