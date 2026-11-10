package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import java.util.List;
import java.util.UUID;

// HU-COM-019 plan D-04: what the deletion writes. Every call is scoped to the tenant; the risk rows are the only content deleted (the consents and the commercial history
// stay). The customer lock serializes two replicas deciding on the same customer, and the record is the lasting trace (one per reservation, never updated).
public interface RetentionDeletions {

    boolean isDeleted(UUID tenantId, UUID reservationId);

    List<UUID> otherReservationsOf(UUID tenantId, UUID customerId, UUID reservationId);

    void lockCustomer(UUID tenantId, UUID customerId);

    int deleteCompanionRiskData(UUID tenantId, UUID reservationId);

    void deleteCustomerRiskData(UUID tenantId, UUID customerId);

    void record(SensitiveDataDeletion deletion);
}
