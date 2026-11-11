package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.util.List;

public record EstablishmentPage(List<AssociatedEstablishment> items, long total) {
    public EstablishmentPage { items = List.copyOf(items); }
}
