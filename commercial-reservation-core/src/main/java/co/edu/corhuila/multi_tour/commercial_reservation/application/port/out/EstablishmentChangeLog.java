package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentChangePage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Optional;
import java.util.UUID;

// Read side of the trace. The entries are written by the repository together with the state they describe.
public interface EstablishmentChangeLog {
    // Newest first, with the identifier as a stable tie-breaker; an empty filter includes every type.
    EstablishmentChangePage listByTenant(UUID tenantId, Optional<EstablishmentType> type, int page, int limit);
}
