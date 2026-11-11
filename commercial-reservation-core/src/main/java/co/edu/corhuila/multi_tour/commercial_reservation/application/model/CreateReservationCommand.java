package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import java.util.List;
import java.util.UUID;

// What the caller chooses (RN-13): who, which tour and departure, how many people (the lead included), the companions
// that travel in the same command (DP-02) and the expected payment. It carries no value; the service quotes its own.
// The codes are the wire codes of DP-08 and may be missing: the creation names each one.
// ownerAccountId is set only for a Final Customer (HU-COM-008): the customer is then the one linked to that account, and
// customerId must be absent (RN-01).
// holder is the new lead customer sent inline, the alternative to customerId (its serviceId is unused: the risk mark is the
// tour's); the service registers it with the reservation in one transaction.
public record CreateReservationCommand(UUID tenantId, UUID actorId, UUID customerId, UUID serviceId, UUID departureId,
        int people, String paymentType, String paymentMethod, List<Companion> companions, UUID ownerAccountId,
        RegisterLeadCustomer.Command holder) {

    public CreateReservationCommand {
        companions = companions == null ? List.of() : List.copyOf(companions);
    }

    public CreateReservationCommand(UUID tenantId, UUID actorId, UUID customerId, UUID serviceId, UUID departureId,
            int people, String paymentType, String paymentMethod, List<Companion> companions, UUID ownerAccountId) {
        this(tenantId, actorId, customerId, serviceId, departureId, people, paymentType, paymentMethod, companions, ownerAccountId, null);
    }

    public CreateReservationCommand(UUID tenantId, UUID actorId, UUID customerId, UUID serviceId, UUID departureId,
            int people, String paymentType, String paymentMethod, List<Companion> companions) {
        this(tenantId, actorId, customerId, serviceId, departureId, people, paymentType, paymentMethod, companions, null, null);
    }

    public CreateReservationCommand(UUID tenantId, UUID actorId, UUID customerId, UUID serviceId, UUID departureId,
            int people, String paymentType, String paymentMethod) {
        this(tenantId, actorId, customerId, serviceId, departureId, people, paymentType, paymentMethod, List.of(), null, null);
    }
}
