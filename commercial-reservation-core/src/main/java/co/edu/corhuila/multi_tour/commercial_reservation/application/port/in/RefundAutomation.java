package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.UUID;

// HU-COM-016: what the service token asks (job 2 and the domain of operations). The service decides if the 24 hours or the five days have passed.
public interface RefundAutomation {

    RefundAuthorization applyDefault(UUID tenantId, UUID financialDecisionId);

    RefundAuthorization markOverdue(UUID tenantId, UUID refundAuthorizationId);

    RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Cents amount);
}
