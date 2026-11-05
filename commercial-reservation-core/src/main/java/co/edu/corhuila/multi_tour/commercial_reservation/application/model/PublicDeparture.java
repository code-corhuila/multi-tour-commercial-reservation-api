package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// One open departure as a browser sees it: start, free seats and whether it is full (a full one is shown but not
// reservable; AC-08.10). No end time, no effective capacity and no held seats (AC-06.8).
public record PublicDeparture(UUID departureId, Instant startsAt, int availableSeats, boolean full) {
}
