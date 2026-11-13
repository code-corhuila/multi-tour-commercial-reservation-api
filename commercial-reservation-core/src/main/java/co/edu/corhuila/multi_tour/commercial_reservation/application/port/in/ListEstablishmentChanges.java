package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentChangePage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;

public interface ListEstablishmentChanges {
    // Newest first; the tenant comes from the actor and never from the request.
    EstablishmentChangePage list(EstablishmentActor actor, Optional<EstablishmentType> type, int page, int limit);
}
