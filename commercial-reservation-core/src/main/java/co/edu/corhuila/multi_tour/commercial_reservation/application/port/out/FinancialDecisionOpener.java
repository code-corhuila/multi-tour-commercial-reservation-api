package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.UUID;

// HU-COM-013 RN-05, AC-13.4: opens the financial decision "Cancelacion solicitada por el cliente" with the base amount, the penalty and the
// start of its 24 running hours. Called inside the transaction and only for a refundable amount above zero. The table and the 24-hour
// default belong to HU-COM-016 and the worker, so a double stands in until then (DP-D13, declared debt DD-2).
public interface FinancialDecisionOpener {

    void open(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt);

    // HU-COM-020 AC-20.7: the same decision opened frozen, with its 24 hours intact, for a request registered while the tenant is inactive.
    default void openFrozen(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) {
        throw new UnsupportedOperationException("the frozen opening needs the implementation override");
    }
}
