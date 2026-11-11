package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// DOCUMENTED STAND-IN (declared debts DD-5, DD-9): the credit balance (HU-COM-017), the treatment of the value of a cancelled
// reservation (HU-COM-016) are other stories; the cancellation for non-payment is JdbcReservationCancellation (HU-COM-011). The excess and the treatment value are
// not lost: the decided attempt keeps them (excess_amount_cents, treatment_amount_cents), and only a reference is logged. The
public final class DeferredPaymentSettlement implements CreditBalanceRecorder, CancelledValueRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredPaymentSettlement.class);

    @Override
    public void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount) {
        LOG.info("Excess kept on the attempt until the credit balance exists, reservationId={}, attemptId={}", reservationId, attemptId);
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Cents value, Origin origin) {
        LOG.info("Value kept on the attempt until the treatment exists, reservationId={}, origin={}", reservationId, origin);
    }
}
