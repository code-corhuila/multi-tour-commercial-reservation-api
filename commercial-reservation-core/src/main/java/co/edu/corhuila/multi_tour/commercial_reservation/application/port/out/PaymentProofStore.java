package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import java.util.UUID;

// The proof goes to its own table in the transaction of the attempt (ADR-005 E6): no proof without file, no file without proof.
public interface PaymentProofStore {

    void save(UUID tenantId, UUID attemptId, ProofFile proof);
}
