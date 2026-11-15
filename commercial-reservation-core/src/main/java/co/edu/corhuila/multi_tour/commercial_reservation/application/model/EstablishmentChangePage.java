package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentChange;
import java.util.List;

public record EstablishmentChangePage(List<EstablishmentChange> items, long total) {
    public EstablishmentChangePage { items = List.copyOf(items); }
}
