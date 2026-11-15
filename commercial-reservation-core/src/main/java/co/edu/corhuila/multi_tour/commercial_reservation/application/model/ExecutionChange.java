package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// What the start or the end of a departure did to its reservations: how many moved and how many kept their state. Counts only, no reservation is named.
public record ExecutionChange(UUID departureId, int changed, int unchanged) {}
