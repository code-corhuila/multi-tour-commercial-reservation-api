package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// One page of decisions or refunds. total counts the whole answer, not the page.
public record RefundPage<T>(List<T> items, long total) {
}
