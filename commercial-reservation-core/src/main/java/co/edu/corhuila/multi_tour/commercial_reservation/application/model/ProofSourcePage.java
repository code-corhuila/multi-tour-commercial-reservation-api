package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// One page of attempts and the total that match, ordered by presentation time.
public record ProofSourcePage(List<ProofSource> items, long total) {
}
