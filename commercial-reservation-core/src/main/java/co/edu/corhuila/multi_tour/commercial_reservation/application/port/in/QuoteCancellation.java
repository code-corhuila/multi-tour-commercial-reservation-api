package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// HU-COM-013 AC-13.3: what cancelling now would give. Nothing is written.
public interface QuoteCancellation {
    CancellationQuote quote(PaymentActor actor, UUID reservationId);
}
