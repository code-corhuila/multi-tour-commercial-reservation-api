package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record InternalDepartureReservationPage(List<InternalDepartureReservation> items, long total) {
    public InternalDepartureReservationPage {
        items = List.copyOf(items);
    }
}
