package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import java.util.UUID;

// The proof goes to its own table in the transaction of the attempt (ADR-005 E6): no proof without file, no file without proof.
public interface PaymentProofStore {

    void save(UUID tenantId, UUID attemptId, ProofFile proof);

    // The download is a use case of its own, never part of a decision or a list (010 plan D-07, G-I). Another tenant's is empty.
    java.util.Optional<ProofFile> read(UUID tenantId, UUID attemptId);
}
