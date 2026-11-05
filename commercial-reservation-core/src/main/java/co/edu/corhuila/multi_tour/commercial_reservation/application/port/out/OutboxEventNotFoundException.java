package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

public final class OutboxEventNotFoundException extends RuntimeException {
    public OutboxEventNotFoundException(UUID eventId) {
        super("outbox event not found: " + eventId);
    }
}
