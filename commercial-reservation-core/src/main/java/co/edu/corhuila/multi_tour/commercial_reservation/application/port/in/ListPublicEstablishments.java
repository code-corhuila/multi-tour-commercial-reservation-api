package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishmentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;

public interface ListPublicEstablishments {
    PublicEstablishmentPage list(String tenantIdentifier, Optional<EstablishmentType> type, int page, int limit);
}
