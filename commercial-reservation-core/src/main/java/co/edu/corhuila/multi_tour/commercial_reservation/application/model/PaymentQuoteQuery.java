package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.UUID;

// The type is optional: without it the quote is for the type the reservation expects. No amount can be sent (AC-09.15).
public record PaymentQuoteQuery(UUID tenantId, UUID reservationId, PaymentType type) {
}
