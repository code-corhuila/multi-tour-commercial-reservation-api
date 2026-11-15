package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Sets the account of a customer record that has none, keeping every other datum and its history (AC-08.14). Returns false when
// the record does not exist in the tenant or already has an account; the -db keeps one customer per account.
public interface CustomerAccountLink {
    boolean linkIfUnlinked(UUID tenantId, UUID customerId, UUID accountId);
}
