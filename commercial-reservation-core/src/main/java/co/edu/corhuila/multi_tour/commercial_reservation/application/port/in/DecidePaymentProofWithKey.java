package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;

// The decision of a proof under an Idempotency-Key (RN-11, AC-10.12): a repeat of the same request answers the stored decision and writes
// nothing; the same key on another proof or with another body is a rule violation.
public interface DecidePaymentProofWithKey {

    DecidedProof reject(RejectProofCommand command, String key);
}
