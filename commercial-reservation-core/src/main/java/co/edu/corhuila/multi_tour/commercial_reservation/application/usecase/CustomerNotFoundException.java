package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import java.util.UUID;

// Also the answer for a customer of another tenant (RN-12): it looks missing, never forbidden.
public final class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(UUID customerId) { super("customer " + customerId + " was not found"); }
}
