package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;

// HU-COM-019 plan D-03: what the local mirror of the departure knows about its end. A row born before the consumer kept the end has no value, which reads as no anchor.
public record DepartureFacts(DepartureStatus status, Instant estimatedEndsAt, Instant finalizedAt) {
}
