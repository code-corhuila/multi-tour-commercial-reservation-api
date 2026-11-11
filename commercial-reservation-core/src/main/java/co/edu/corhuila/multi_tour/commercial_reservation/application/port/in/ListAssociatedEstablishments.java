package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;

public interface ListAssociatedEstablishments {
    EstablishmentPage list(EstablishmentActor actor, Optional<AssociationStatus> status,
            Optional<EstablishmentType> type, int page, int limit);
}
