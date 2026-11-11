package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The open deadline of a reservation, if any (G-DL): a reservation with no row is not refused for expiry. The row is
// written by HU-COM-009's db; HU-COM-011 extends and freezes it.
public interface PaymentDeadlineReader {

    // The row with the latest due instant: what a payment, a quote and the screens judge.
    Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId);

    // Every row of the reservation, latest due instant first: the expiry judges each one (HU-COM-011, PDR 1.9 line 842).
    default List<PaymentDeadline> openDeadlines(UUID tenantId, UUID reservationId) {
        return openDeadline(tenantId, reservationId).stream().toList();
    }
}
