package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.UUID;

public interface GetAssociatedEstablishment {
    AssociatedEstablishment get(EstablishmentActor actor, UUID associationId);
}
