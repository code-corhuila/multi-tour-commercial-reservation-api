package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationFacts;
import java.util.UUID;

// HU-COM-019 plan D-03: the facts that decide the anchor of one reservation, with the customer it belongs to. No datum of the customer travels here.
public record RetentionFacts(UUID customerId, DepartureFacts departure, ReservationFacts reservation) {
}
