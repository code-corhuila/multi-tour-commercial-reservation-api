package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// HU-COM-013: the user and the instant never come from the body; the reason is free text and the only origin this route registers is
// CUSTOMER_REQUEST (DP-04).
public record CancelReservationCommand(PaymentActor actor, UUID reservationId, String reason, String origin) {
}
