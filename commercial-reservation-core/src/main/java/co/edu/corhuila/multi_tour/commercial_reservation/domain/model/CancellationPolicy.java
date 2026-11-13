package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// HU-COM-013 DP-02a: the penalty of a customer cancellation for a service, or for the whole tenant when serviceId is empty, in basis points (0 to 10 000, 0 is no
// penalty). The row keeps only the last change: its author, its reason and its time.
public record CancellationPolicy(UUID serviceId, int penaltyBasisPoints, UUID changedBy, String changeReason, Instant changedAt) {
    public static final int MAX_BASIS_POINTS = 10_000;

    public CancellationPolicy {
        if (penaltyBasisPoints < 0 || penaltyBasisPoints > MAX_BASIS_POINTS) {
            throw new DomainException("penaltyBasisPoints", "penaltyBasisPoints must be from 0 to " + MAX_BASIS_POINTS);
        }
    }
}
