package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import java.util.List;
import java.util.UUID;

// HU-COM-020 RN-02, RN-03, RN-06: the open deadline rows of one tenant, and the write of a frozen or resumed row. It reads and writes the rows of that tenant only; called inside the transaction of
// the tenant event.
public interface TenantDeadlineStore {

    List<TenantDeadline> openOf(UUID tenantId);

    void save(TenantDeadline row);
}
