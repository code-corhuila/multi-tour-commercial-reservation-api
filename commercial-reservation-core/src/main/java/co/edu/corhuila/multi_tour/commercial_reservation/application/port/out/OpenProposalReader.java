package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// HU-COM-014 plan D-05: whether a rescheduling proposal of the operator is open on a reservation, so the customer is offered no payment
// action while it is. The proposal is HU-COM-018's; until it exists the adapter answers false.
public interface OpenProposalReader {

    boolean isOpen(UUID tenantId, UUID reservationId);
}
