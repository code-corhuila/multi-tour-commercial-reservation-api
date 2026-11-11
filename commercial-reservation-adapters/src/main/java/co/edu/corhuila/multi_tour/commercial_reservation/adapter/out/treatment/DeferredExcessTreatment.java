package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Plan D-06: the pending treatment of an excess is asked of HU-COM-016/017, which are not built, and its event is NO DEFINIDO.
// Until then the port is answered without deciding anything about money. An excess needs a validated payment above the
// new value; the paid amount is read from the reservation (HU-COM-009/010), so the port can now be reached.
public final class DeferredExcessTreatment implements ExcessTreatment {
    @Override
    public void open(UUID tenantId, UUID reservationId, Cents excess) {
        // TODO D-06: ask HU-COM-016/017 to open the treatment (24 hours, 100 % refund by default, PROPOSED).
    }
}
