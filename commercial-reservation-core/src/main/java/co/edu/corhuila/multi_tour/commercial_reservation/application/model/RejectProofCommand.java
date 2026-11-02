package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// A rejection takes only the reason: an amount sent with it is not stored (010 plan section 2).
public record RejectProofCommand(PaymentActor actor, UUID attemptId, String reason) {
}
