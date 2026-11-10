package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AnchorBasis;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// HU-COM-019 plan section 2: one page of the reservations whose sensitive data is due. A row carries identifiers, the basis and the anchor instant, never a datum; `next` is the
// cursor of the following page (the id of the last candidate read) or null when the list ends.
public record DuePage(List<Row> rows, UUID next) {
    public record Row(UUID reservationId, UUID customerId, AnchorBasis basis, Instant anchorAt) {}
}
