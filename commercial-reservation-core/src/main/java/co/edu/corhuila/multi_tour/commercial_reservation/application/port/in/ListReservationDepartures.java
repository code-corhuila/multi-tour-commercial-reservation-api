package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeparturePage;
import java.util.UUID;

// The one route of the departures of a tour for HU-COM-006, 007 and 008; the full ones are kept (AC-07.7).
public interface ListReservationDepartures {

    DeparturePage list(UUID tenantId, UUID serviceId, int page, int limit);
}
