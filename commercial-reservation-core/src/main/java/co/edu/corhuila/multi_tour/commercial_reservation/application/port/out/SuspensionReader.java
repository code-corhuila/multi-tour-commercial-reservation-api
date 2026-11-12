package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-020 RN-10, AC-20.12, plan D-07: the read side of the suspension history, for the decisions taken while the tenant is inactive. Empty means the tenant has no open suspension.
public interface SuspensionReader {

    // The instant at which the open suspension of the tenant began; empty when none is open.
    Optional<Instant> openSince(UUID tenantId);
}
