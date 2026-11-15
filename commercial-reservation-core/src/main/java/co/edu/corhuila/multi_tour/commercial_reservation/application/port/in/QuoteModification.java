package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationRequest;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import java.util.UUID;

// HU-COM-012 AC-12.2: what changing the reservation now would give. Nothing is written.
public interface QuoteModification {
    ModificationQuote quote(PaymentActor actor, UUID reservationId, ModificationRequest request);
}
