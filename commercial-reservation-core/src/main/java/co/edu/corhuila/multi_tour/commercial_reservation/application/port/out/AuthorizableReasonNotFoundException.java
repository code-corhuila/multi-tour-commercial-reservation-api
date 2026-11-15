package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

public class AuthorizableReasonNotFoundException extends RuntimeException {
    public AuthorizableReasonNotFoundException(UUID reasonId) { super("reason " + reasonId + " not found"); }
}
