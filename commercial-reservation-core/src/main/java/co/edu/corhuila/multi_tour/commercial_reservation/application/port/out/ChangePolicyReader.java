package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import java.util.Optional;
import java.util.UUID;

// HU-COM-012 plan D-04, DP-01: the terms of a tour for a change type, or empty when no policy exists (the default: the change is refused).
// The use case compares the lead time; the aggregate does not know the terms. Declared debt DD-2: until the adapter reads the table of the
// db repository, the only implementation answers empty.
public interface ChangePolicyReader {

    Optional<ChangePolicy> policyFor(UUID tenantId, UUID serviceId, ChangeType changeType);
}
