package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// Only the note comes from the caller: the user from the token and the time from the clock (AC-11.5).
public record RecordFollowupCommand(PaymentActor actor, UUID reservationId, String note) {
}
