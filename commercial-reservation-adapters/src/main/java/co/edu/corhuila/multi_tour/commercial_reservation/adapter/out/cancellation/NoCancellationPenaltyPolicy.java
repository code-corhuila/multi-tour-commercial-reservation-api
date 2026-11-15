package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cancellation;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.UUID;

// DOCUMENTED STAND-IN (HU-COM-013 DP-02, declared debt DD-3): the penalty has no home in the data (06-data says no column may be created
// while its content is undefined), so no policy exists and the cancellation carries no penalty: the whole validated value is refundable.
public final class NoCancellationPenaltyPolicy implements CancellationPolicyReader {

    @Override
    public OptionalInt penaltyBasisPoints(UUID tenantId, UUID serviceId, Instant departureStart, Instant now) {
        return OptionalInt.empty();
    }
}
