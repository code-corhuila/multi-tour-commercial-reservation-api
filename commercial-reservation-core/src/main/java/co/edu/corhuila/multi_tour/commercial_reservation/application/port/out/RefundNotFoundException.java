package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also the answer for a decision or a refund of another tenant (AC-16.13): it looks missing, never forbidden.
public class RefundNotFoundException extends RuntimeException {
    public RefundNotFoundException(String what, UUID id) { super(what + " " + id + " not found"); }
}
