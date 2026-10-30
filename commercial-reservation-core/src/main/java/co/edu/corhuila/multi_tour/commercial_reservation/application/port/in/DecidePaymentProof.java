package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApproveProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;

// Approves or rejects a transfer proof in validation. Only the holder of the permission decides and a decision is final.
public interface DecidePaymentProof {

    DecidedProof approve(ApproveProofCommand command);

    DecidedProof reject(RejectProofCommand command);
}
