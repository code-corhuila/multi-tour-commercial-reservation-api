package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.List;

// One page of the history of a reservation, newest first.
public record FollowupPage(List<PaymentFollowup> items, long total) {
    public FollowupPage {
        items = List.copyOf(items);
    }
}
