package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;

// HU-COM-019 plan D-03: what the reservation says about its own end; nothingToTreat tells that a cancelled reservation has no value left to decide (RN-02).
public record ReservationFacts(ReservationStatus status, Instant cancelledAt, boolean nothingToTreat) {
}
