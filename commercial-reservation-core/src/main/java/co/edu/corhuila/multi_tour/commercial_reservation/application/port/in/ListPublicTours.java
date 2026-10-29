package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourPage;
import java.util.Optional;

public interface ListPublicTours {
    PublicTourPage list(String tenantIdentifier, Optional<String> search, int page, int limit);
}
