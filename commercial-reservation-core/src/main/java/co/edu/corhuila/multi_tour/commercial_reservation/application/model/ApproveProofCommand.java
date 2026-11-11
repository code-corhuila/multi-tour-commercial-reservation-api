package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// The reviewer sets the verified amount, in whole cents; the reason is mandatory when it differs from the declared one (DP-06).
public record ApproveProofCommand(PaymentActor actor, UUID attemptId, Long verifiedAmountCents, String reason) {
}
