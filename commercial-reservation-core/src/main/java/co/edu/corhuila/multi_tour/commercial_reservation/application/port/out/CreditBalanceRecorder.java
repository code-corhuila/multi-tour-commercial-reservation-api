package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// The excess over the pending balance is handed to the credit balance of HU-COM-017, never dropped (010 AC-10.9). A double
// until that story exists; no credit balance is computed here.
public interface CreditBalanceRecorder {

    void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount);
}
