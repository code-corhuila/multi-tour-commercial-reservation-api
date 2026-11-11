package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordValidatedPaymentCommand;

// Records the payment of an approved proof as validated, recomputes the situation and confirms the reservation (saga step 2).
// Idempotent: the same decision answers the stored one and writes nothing; any other repeat is a final decision.
public interface RecordValidatedPayment {

    DecidedProof record(RecordValidatedPaymentCommand command);
}
