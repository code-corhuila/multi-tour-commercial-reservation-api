package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import java.util.List;
import java.util.UUID;

// What the office asks to change on a reservation (HU-COM-012 plan section 2). The tour is the reserved one; changing it arrives with the adapters.
public record ModificationRequest(ChangeType changeType, int partySize, UUID departureId, List<Companion> companions) {
}
