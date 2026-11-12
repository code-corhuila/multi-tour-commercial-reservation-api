package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

// One page of pending events. total counts every event ready to deliver, not the page.
public record OutboxEventPage(List<OutboxEventView> items, long total) {
}
