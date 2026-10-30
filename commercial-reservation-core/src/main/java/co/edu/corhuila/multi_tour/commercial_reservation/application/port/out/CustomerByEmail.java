package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.Optional;
import java.util.UUID;

// The customer record of a tenant that carries a normalized e-mail (CA-024, AC-05.7). verified is true only when the record
// is linked to a verified account; a record with no account is an unverified match.
public interface CustomerByEmail {
    record EmailMatch(UUID customerId, boolean verified) {}

    Optional<EmailMatch> findByTenantAndEmail(UUID tenantId, String normalizedEmail);
}
