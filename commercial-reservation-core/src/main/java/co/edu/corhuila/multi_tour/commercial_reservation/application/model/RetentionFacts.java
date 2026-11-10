package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationFacts;
import java.time.ZoneId;
import java.util.UUID;

// HU-COM-019 plan D-03, D-07: the facts that decide the anchor of one reservation, with the customer it belongs to and the zone of its tenant (the platform default when
// the tenant has no copy yet). No datum of the customer travels here.
public record RetentionFacts(UUID customerId, ZoneId zone, DepartureFacts departure, ReservationFacts reservation) {
}
