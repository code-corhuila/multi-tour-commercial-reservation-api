package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record PublicTourPage(List<PublicTour> items, long total) {
    public PublicTourPage { items = List.copyOf(items); }
}
