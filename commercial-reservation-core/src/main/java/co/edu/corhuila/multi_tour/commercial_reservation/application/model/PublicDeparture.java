package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.UUID;

// One bookable departure as a browser sees it: start and free seats only (plan D-02, DP-07). No end time,
// no effective capacity and no held seats (AC-06.8).
public record PublicDeparture(UUID departureId, Instant startsAt, int availableSeats) {
}
