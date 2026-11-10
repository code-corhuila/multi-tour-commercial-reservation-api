package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;

// One reservation of a departure as operations-cost reads it: the name of the lead customer, the whole group and the state. Nothing else about the customer.
public record DepartureReservation(UUID reservationId, String customerName, int people, ReservationStatus reservationStatus) {}
