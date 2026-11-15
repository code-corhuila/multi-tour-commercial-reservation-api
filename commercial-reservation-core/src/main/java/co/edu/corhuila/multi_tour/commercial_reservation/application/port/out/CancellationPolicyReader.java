package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;
import java.util.OptionalInt;
import java.util.UUID;

// HU-COM-013 plan D-04, DP-02: the penalty percentage (basis points) valid at the moment of the cancellation, or empty when no policy
// exists (the default: no penalty). The penalty has no home in the data yet, so the only adapter answers empty (declared debt DD-3).
public interface CancellationPolicyReader {

    OptionalInt penaltyBasisPoints(UUID tenantId, UUID serviceId, Instant departureStart, Instant now);
}
