package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.treatment;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// DOCUMENTED STAND-IN (declared debts DD-5, DD-9): the credit balance (HU-COM-017), the treatment of the value of a cancelled
// reservation (HU-COM-016) and the cancellation for non-payment (HU-COM-011) are other stories. The excess and the treatment value are
// not lost: the decided attempt keeps them (excess_amount_cents, treatment_amount_cents), and only a reference is logged. The
// cancellation refuses instead of pretending: it is reached only when a payment deadline has expired, and no deadline row is written yet.
public final class DeferredPaymentSettlement implements CreditBalanceRecorder, CancelledValueRecorder, ReservationCancellation {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredPaymentSettlement.class);

    @Override
    public void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount) {
        LOG.info("Excess kept on the attempt until the credit balance exists, reservationId={}, attemptId={}", reservationId, attemptId);
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Cents value, Origin origin) {
        LOG.info("Value kept on the attempt until the treatment exists, reservationId={}, origin={}", reservationId, origin);
    }

    @Override
    public void cancelForNonPayment(UUID tenantId, UUID reservationId, Instant now) {
        throw new BusinessRuleViolation("the cancellation for non-payment is not available yet");
    }
}
