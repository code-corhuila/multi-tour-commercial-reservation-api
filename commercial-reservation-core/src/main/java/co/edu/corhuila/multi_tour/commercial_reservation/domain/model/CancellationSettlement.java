package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.OptionalInt;

// HU-COM-013 RN-04, AC-13.3: what a cancellation at the customer's request gives back. The penalty is the policy percentage (basis points)
// of the validated values, rounded down to the cent so the customer is never charged more than the percentage (plan D-03, DP-02), and it
// never exceeds them. With no policy the penalty is zero and the whole validated value is refundable.
public record CancellationSettlement(Cents validated, int penaltyBasisPoints, Cents penalty, Cents refundable) {

    public static final int MAXIMUM_BASIS_POINTS = 10_000;

    public static CancellationSettlement of(Cents validated, OptionalInt basisPoints) {
        int points = basisPoints.orElse(0);
        if (points < 0 || points > MAXIMUM_BASIS_POINTS) {
            throw new DomainException("penaltyBasisPoints", "the penalty must be between 0 and 10000 basis points");
        }
        long penalty = Math.multiplyExact(validated.amount(), (long) points) / MAXIMUM_BASIS_POINTS;
        return new CancellationSettlement(validated, points, new Cents(penalty), new Cents(validated.amount() - penalty));
    }
}
