package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

public interface CollaboratorEditingPolicy {
    /** Whether the tenant enabled its Operational Collaborator to update the descriptive information of the catalog (RF-004). */
    boolean allowsDescriptiveUpdates(UUID tenantId);
}
