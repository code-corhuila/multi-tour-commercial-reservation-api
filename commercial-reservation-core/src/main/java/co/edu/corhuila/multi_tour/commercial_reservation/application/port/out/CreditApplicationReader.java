package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import java.util.Optional;
import java.util.UUID;

// HU-COM-017 AC-17.15: a repeated application with the same Idempotency-Key answers the stored record; the read is scoped to the tenant.
public interface CreditApplicationReader {

    Optional<CreditApplication> findApplication(UUID tenantId, UUID applicationId);
}
