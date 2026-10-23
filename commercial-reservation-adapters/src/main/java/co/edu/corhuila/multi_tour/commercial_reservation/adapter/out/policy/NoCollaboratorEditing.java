package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.policy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CollaboratorEditingPolicy;
import java.util.UUID;

// No source for the tenant enabling exists yet (DD-19, HU-SEC-006): until it does, no tenant has enabled the Collaborator,
// which is the behavior the API had before the port. Replace this adapter, not the callers, when the source arrives.
public final class NoCollaboratorEditing implements CollaboratorEditingPolicy {
    @Override
    public boolean allowsDescriptiveUpdates(UUID tenantId) {
        return false;
    }
}
