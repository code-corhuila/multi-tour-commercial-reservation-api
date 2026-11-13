package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;

// Rejects a transfer proof in validation (the approval is the step 2 of the saga register-payment). Only the holder of the permission decides and a decision is final.
public interface DecidePaymentProof {

    DecidedProof reject(RejectProofCommand command);
}
