package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.UUID;

// HU-COM-017 AC-17.10, AC-17.11: ask for the refund of a credit balance. The tenant, the person, the role and, for a Final Customer, their own customer id come from
// the token; the body carries the amount and, when staff asks, the reason (never stored, logged or evented).
public record RequestCreditRefundCommand(UUID tenantId, UUID actorId, Role role, UUID actorCustomerId, UUID creditBalanceId, long amountCents, String reason) {
}
