package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeparturePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Scheduled departures of the tour that have not started; a full one stays and the caller marks it (AC-07.7).
public class ListReservationDeparturesUseCase implements ListReservationDepartures {

    private final CatalogServiceRepository catalog;
    private final DepartureReader departures;
    private final Clock clock;

    public ListReservationDeparturesUseCase(CatalogServiceRepository catalog, DepartureReader departures, Clock clock) {
        this.catalog = catalog;
        this.departures = departures;
        this.clock = clock;
    }

    @Override
    public DeparturePage list(UUID tenantId, UUID serviceId, int page, int limit) {
        CatalogLookup.require(catalog, tenantId, serviceId);
        Instant now = Instant.now(clock);
        List<DepartureState> open = departures.findOpen(tenantId, serviceId, now).stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.serviceId().equals(serviceId))
                .filter(row -> row.status() == DepartureStatus.SCHEDULED && now.isBefore(row.startsAt()))
                .sorted(Comparator.comparing(DepartureState::startsAt)).toList();
        int from = (int) Math.min((long) (page - 1) * limit, open.size());
        return new DeparturePage(open.subList(from, Math.min(from + limit, open.size())), open.size());
    }
}
