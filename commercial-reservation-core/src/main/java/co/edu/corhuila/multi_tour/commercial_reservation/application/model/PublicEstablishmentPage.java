package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record PublicEstablishmentPage(List<PublicEstablishment> items, long total) {
    public PublicEstablishmentPage { items = List.copyOf(items); }
}
