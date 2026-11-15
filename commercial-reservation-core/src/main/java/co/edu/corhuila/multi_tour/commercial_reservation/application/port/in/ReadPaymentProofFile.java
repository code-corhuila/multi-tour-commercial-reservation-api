package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import java.util.UUID;

// The file of a proof, read through its own use case and never inside a decision or a list (AC-10.19).
public interface ReadPaymentProofFile {

    ProofFile read(PaymentActor actor, UUID attemptId);
}
