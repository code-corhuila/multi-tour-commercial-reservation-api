package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// One page of the deadlines that were reached, for the job. total counts the whole answer, not the page.
public record DueDeadlinePage(List<DeadlineView> items, long total) {
    public DueDeadlinePage {
        items = List.copyOf(items);
    }
}
