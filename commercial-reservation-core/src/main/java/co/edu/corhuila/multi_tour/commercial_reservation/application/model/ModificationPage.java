package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// One page of the history of the modifications of a reservation, newest first.
public record ModificationPage(List<ReservationModificationRecord> items, long total) {
    public ModificationPage {
        items = List.copyOf(items);
    }
}
