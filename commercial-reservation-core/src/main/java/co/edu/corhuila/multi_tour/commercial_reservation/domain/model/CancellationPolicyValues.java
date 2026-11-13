package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.UUID;

// HU-COM-013 DP-02a: what the Administrator sends for one policy row. An empty serviceId is the policy of the whole tenant. The percentage is nullable here so the
// use case, after the role check, can refuse a missing one naming the field.
public record CancellationPolicyValues(UUID serviceId, Integer penaltyBasisPoints) {
}
