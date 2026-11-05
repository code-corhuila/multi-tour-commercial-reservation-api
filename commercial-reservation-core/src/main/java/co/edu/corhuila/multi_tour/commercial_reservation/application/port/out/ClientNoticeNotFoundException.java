package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// The notice does not exist for the caller: unknown, of another tenant, not a notice, or without a recipient address to send to (RN-07, RN-10).
public final class ClientNoticeNotFoundException extends RuntimeException {
    public ClientNoticeNotFoundException(UUID eventId) {
        super("client notice not found: " + eventId);
    }
}
