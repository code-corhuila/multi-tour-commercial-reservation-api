package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import java.util.Optional;
import java.util.UUID;

// The open deadline of a reservation, if any (G-DL): a reservation with no row is not refused for expiry. The row is
// written by HU-COM-009's db; HU-COM-011 extends and freezes it.
public interface PaymentDeadlineReader {

    Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId);
}
