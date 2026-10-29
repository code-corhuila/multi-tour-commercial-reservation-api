package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.UUID;

// What the caller chooses (RN-13): who, which tour and departure, how many people and the expected payment. It carries no
// value; the service quotes its own. The codes are the wire codes of DP-08 and may be missing: the creation names each one.
public record CreateReservationCommand(UUID tenantId, UUID actorId, UUID customerId, UUID serviceId, UUID departureId,
        int people, String paymentType, String paymentMethod) {
}
