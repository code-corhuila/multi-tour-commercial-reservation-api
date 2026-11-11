package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// The page of the list; the response carries the overdue count for the panel alert (010 plan section 2).
public record ProofPage(List<ProofView> items, int page, int limit, long total, long overdueCount) {
}
