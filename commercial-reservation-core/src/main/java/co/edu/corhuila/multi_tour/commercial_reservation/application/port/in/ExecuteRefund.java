package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.util.UUID;

// HU-COM-016 D-06: the steps of the saga execute-refund that this service owns: step 1 (start), its compensation (undo) and step 3 (completion). The
// service never calls Cash. The tenant is the path of the internal route; each step is idempotent by state.
public interface ExecuteRefund {

    RefundAuthorization start(UUID tenantId, UUID refundAuthorizationId, UUID executedBy, OutflowMethod method);

    RefundAuthorization undo(UUID tenantId, UUID refundAuthorizationId);

    RefundAuthorization complete(UUID tenantId, UUID refundAuthorizationId, UUID cashMovementId);

    RefundAuthorization find(UUID tenantId, UUID refundAuthorizationId);
}
