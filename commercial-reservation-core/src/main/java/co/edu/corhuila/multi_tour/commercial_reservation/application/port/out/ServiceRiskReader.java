package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.Optional;
import java.util.UUID;

// Reads the catalog mark highRisk (plan D-04, DP-01 default). Empty when the service does not exist for the
// tenant, including a service of another tenant.
public interface ServiceRiskReader {
    Optional<Boolean> isHighRisk(UUID tenantId, UUID serviceId);
}
