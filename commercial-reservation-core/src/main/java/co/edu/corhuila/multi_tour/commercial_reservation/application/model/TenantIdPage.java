package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;
import java.util.UUID;

// One page of tenant ids and nothing else: no name, no identifier, no personal data. total counts the whole answer, not the page.
public record TenantIdPage(List<UUID> items, long total) {
    public TenantIdPage {
        items = List.copyOf(items);
    }
}
