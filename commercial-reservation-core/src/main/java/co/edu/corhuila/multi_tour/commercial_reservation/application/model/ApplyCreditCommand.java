package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import java.util.UUID;

// HU-COM-017 AC-17.4 to AC-17.8: apply a credit balance to a reservation. The tenant, the person, the role and, for a Final Customer, their own customer id come from
// the token; the body carries only the target reservation and the amount (the route adds the credit from the path).
public record ApplyCreditCommand(UUID tenantId, UUID actorId, Role role, UUID actorCustomerId, UUID creditBalanceId, UUID targetReservationId, long amountCents) {
}
