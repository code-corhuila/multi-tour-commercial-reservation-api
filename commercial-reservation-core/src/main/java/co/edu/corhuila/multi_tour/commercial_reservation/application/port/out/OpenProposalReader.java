package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// HU-COM-014 plan D-05: whether a rescheduling proposal of the operator is open on a reservation, so the customer is offered no payment
// action while it is. The proposal is HU-COM-018's: the adapter reads a PENDING proposal of an OPEN episode (JdbcOpenProposal).
public interface OpenProposalReader {

    boolean isOpen(UUID tenantId, UUID reservationId);
}
