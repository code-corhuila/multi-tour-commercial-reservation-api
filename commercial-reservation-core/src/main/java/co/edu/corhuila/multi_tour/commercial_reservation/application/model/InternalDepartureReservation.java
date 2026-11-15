package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;

// One reservation of a departure as a service reads it (listInternalDepartureReservations): the state, the people, the id of the lead customer and what was
// validated so far. No name, document or contact of anyone.
public record InternalDepartureReservation(UUID reservationId, ReservationStatus status, int travelers, UUID customerId, long validatedAmountCents) {}
