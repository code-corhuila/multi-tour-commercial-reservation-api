package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import java.util.UUID;

// Quotes the value of a tour for a departure and a number of people; it holds nothing and stores nothing (AC-07.3).
public interface QuoteReservation {

    ReservationQuote quote(UUID tenantId, UUID serviceId, UUID departureId, int people);
}
