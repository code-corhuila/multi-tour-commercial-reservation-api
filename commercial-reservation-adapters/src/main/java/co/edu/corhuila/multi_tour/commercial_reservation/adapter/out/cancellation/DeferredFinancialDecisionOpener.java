package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// DOCUMENTED STAND-IN (HU-COM-013 AC-13.4, declared debt DD-2): the financial decision and its table belong to HU-COM-016 and the worker
// of the 24-hour default (HU-WRK-002); none is built. The amounts are not lost: the cancellation record keeps the validated value, the
// penalty and the refundable amount, and a refundable amount above zero marks refundDecisionOpened. Only a reference is logged.
public final class DeferredFinancialDecisionOpener implements FinancialDecisionOpener {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredFinancialDecisionOpener.class);

    @Override
    public void open(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) {
        LOG.info("Financial decision kept on the cancellation record until HU-COM-016 exists, reservationId={}", reservationId);
    }
}
