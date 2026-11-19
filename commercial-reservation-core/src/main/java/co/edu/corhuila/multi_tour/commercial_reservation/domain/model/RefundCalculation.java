package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.math.BigInteger;

// HU-COM-016 (spec 026, AC-26.4, AC-26.5): what a personal emergency refunds. The share is the part of the validated value that belongs to the affected
// travelers, rounded down; the penalty is the policy percentage (basis points) of that share, rounded down so the customer is never charged above the
// percentage (as in CancellationSettlement); the refund is the share minus the penalty. Exact integers: the product of three factors is held in a BigInteger.
public record RefundCalculation(Cents validated, Cents share, int penaltyBasisPoints, Cents penalty, Cents refund) {

    public static RefundCalculation of(Cents validated, int people, int affectedTravelers, int basisPoints) {
        if (people < 1) throw new DomainException("people", "the reservation has no people");
        if (affectedTravelers < 1) throw new DomainException("affectedTravelers", "affectedTravelers must be at least 1");
        if (affectedTravelers > people) throw new BusinessRuleViolation("affectedTravelers is above the people of the reservation");
        if (basisPoints < 0 || basisPoints > CancellationSettlement.MAXIMUM_BASIS_POINTS)
            throw new DomainException("penaltyBasisPoints", "the penalty must be between 0 and 10000 basis points");
        long share = BigInteger.valueOf(validated.amount()).multiply(BigInteger.valueOf(affectedTravelers)).divide(BigInteger.valueOf(people)).longValueExact();
        long penalty = BigInteger.valueOf(share).multiply(BigInteger.valueOf(basisPoints))
                .divide(BigInteger.valueOf(CancellationSettlement.MAXIMUM_BASIS_POINTS)).longValueExact();
        return new RefundCalculation(validated, new Cents(share), basisPoints, new Cents(penalty), new Cents(share - penalty));
    }
}
