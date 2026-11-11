package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also the answer for a credit balance of another tenant (AC-17.14): it looks missing, never forbidden.
public class CreditBalanceNotFoundException extends RuntimeException {
    public CreditBalanceNotFoundException(UUID id) { super("credit balance " + id + " not found"); }
}
