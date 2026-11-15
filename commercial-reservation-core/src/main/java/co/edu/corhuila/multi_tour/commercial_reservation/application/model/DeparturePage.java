package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.util.List;

public record DeparturePage(List<DepartureState> items, long total) {
    public DeparturePage {
        items = List.copyOf(items);
    }
}
