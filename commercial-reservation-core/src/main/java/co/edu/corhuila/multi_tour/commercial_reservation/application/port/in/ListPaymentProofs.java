package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ListProofsQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofPage;
import java.util.UUID;

// The list of proofs for the panel and the overdue list for job 2, which takes the tenant from its path (a service token only,
// enforced by the adapter). Read only: the mark changes no state.
public interface ListPaymentProofs {

    ProofPage list(ListProofsQuery query);

    ProofPage overdueOf(UUID tenantId, int page, int limit);
}
