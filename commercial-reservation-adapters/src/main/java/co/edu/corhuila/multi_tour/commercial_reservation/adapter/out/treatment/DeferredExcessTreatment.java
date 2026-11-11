package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;

// Plan D-06: the pending treatment of an excess is asked of HU-COM-016/017, which are not built, and its event is NO DEFINIDO.
// Until then the port is answered without deciding anything about money. Nothing can reach it yet: what is paid stays at
// zero until HU-COM-010, and an excess needs a payment above the new value.
public final class DeferredExcessTreatment implements ExcessTreatment {
    @Override
    public void open(UUID tenantId, UUID reservationId, Cents excess) {
        // TODO D-06: ask HU-COM-016/017 to open the treatment (24 hours, 100 % refund by default, PROPOSED).
    }
}
