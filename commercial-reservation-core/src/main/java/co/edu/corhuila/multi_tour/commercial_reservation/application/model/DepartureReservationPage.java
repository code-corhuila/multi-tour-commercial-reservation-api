package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record DepartureReservationPage(List<DepartureReservation> items, long total) {
    public DepartureReservationPage {
        items = List.copyOf(items);
    }
}
