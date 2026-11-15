package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import java.util.UUID;

// The full name of a customer of the tenant, to name it in an answer; empty when it is missing or belongs to another tenant.
public interface ReadCustomerName {
    String nameOf(UUID tenantId, UUID customerId);
}
