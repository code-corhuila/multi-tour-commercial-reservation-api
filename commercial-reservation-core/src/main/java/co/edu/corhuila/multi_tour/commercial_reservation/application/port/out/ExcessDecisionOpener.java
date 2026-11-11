package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.UUID;

// HU-COM-012 RN-05, AC-12.4: hands the excess of a modification to the decision "Excedente por modificacion" (24 running hours). Called inside
// the transaction and only for an excess above zero, so the money is never dropped. The decision belongs to HU-COM-016, which is not built:
// a double stands in (declared debt DD-3, DP-D12-4).
public interface ExcessDecisionOpener {

    void open(UUID tenantId, UUID reservationId, Cents excess, Instant recognizedAt);
}
