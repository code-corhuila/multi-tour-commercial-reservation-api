package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// The domain never mints identifiers, so tests can predict them.
public interface IdGenerator {

    UUID newId();
}
