package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordValidatedPaymentCommand;
import java.util.UUID;

// Records the payment of an approved proof as validated, recomputes the situation and confirms the reservation (saga step 2).
// Idempotent: the same decision answers the stored one and writes nothing; any other repeat is a final decision.
public interface RecordValidatedPayment {

    DecidedProof record(RecordValidatedPaymentCommand command);

    // G-WF2-7 (HU-WFL-002, AC-WF2-12): the saga reads, with the tenant of the path, whether the payment is validated; it writes nothing.
    // A payment that is not validated (or of another tenant) is not found.
    DecidedProof find(UUID tenantId, UUID paymentId);
}
