package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import java.util.UUID;

// What job 7 asks of the outbox of this service (ADR-004 D4): the pending events, the mark of a delivered one and the record of a failed delivery.
public interface RelayOutboxDelivery {

    OutboxEventPage pending(int page, int limit);

    OutboxEventView markPublished(UUID eventId);

    OutboxEventView recordAttempt(UUID eventId, String error);
}
