package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// The user and the instant never come from the caller: the actor from the token, the instant from the clock.
public record ExtendDeadlineCommand(PaymentActor actor, UUID reservationId, Integer hours) {
}
