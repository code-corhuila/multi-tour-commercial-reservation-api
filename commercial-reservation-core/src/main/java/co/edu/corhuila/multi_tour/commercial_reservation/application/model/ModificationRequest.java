package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import java.util.List;
import java.util.UUID;

// What the office asks to change on a reservation (HU-COM-012 plan section 2). A field that is absent (null) stays as it is. The tour is the
// reserved one: naming another is refused until changing the tour arrives with the adapters.
public record ModificationRequest(ChangeType changeType, Integer partySize, UUID departureId, List<Companion> companions, UUID serviceId) {

    public ModificationRequest(ChangeType changeType, Integer partySize, UUID departureId, List<Companion> companions) {
        this(changeType, partySize, departureId, companions, null);
    }
}
