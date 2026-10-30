package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record ReservationPage(List<ReservationListItem> items, long total) {
    public ReservationPage {
        items = List.copyOf(items);
    }
}
