package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// HU-COM-018 AC-18.18: an episode that does not exist for the tenant of the token answers as a missing resource, never as forbidden.
public class ReschedulingNotFoundException extends RuntimeException {
    public ReschedulingNotFoundException(UUID episodeId) { super("rescheduling " + episodeId + " not found"); }
}
