package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// Step 2 of the saga register-payment (HU-COM-010 DP-11): the tenant comes from the internal route, the approver is the user the
// saga forwards (the human approval happened before the saga) and the amount and reason are the ones the reviewer set.
public record RecordValidatedPaymentCommand(UUID tenantId, UUID approvedBy, UUID attemptId, Long verifiedAmountCents, String reason) {
}
