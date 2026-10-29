package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Event ids already applied (RN-11). Written in the same transaction as the effect.
public interface ProcessedEvents {

    boolean wasProcessed(UUID eventId);

    void record(UUID eventId);
}
