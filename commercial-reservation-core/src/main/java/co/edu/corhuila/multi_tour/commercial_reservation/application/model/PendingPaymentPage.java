package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.util.List;

public record PendingPaymentPage(List<PendingPaymentRow> items, long total) {
    public PendingPaymentPage {
        items = List.copyOf(items);
    }
}
