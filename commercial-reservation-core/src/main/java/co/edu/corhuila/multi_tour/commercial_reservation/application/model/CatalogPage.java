package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.util.List;

public record CatalogPage(List<CatalogService> items, long total) {
    public CatalogPage {
        items = List.copyOf(items);
    }
}
