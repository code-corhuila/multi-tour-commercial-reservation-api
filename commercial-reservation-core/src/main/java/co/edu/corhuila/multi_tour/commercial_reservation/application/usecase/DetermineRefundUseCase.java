package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundBasisReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoRefundDueException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundCalculation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

// HU-COM-016 spec 026 (AC-26.1 to AC-26.8): the service computes the amount, never the caller. A cause not attributable refunds 100 percent of the paid and
// validated value. A personal emergency refunds the share of the affected travelers minus the policy percentage read at the instant of the cause; with no
// policy row the refund is zero (CA-028, default of DP-26.2). A refund already determined for the same reservation and origin is returned, not repeated. A
// reservation of another tenant looks missing.
public class DetermineRefundUseCase implements DetermineRefund {

    private final RefundBasisReader basis;
    private final CancellationPolicyReader policy;
    private final RefundAuthorizationRepository refunds;
    private final RefundAutomation automation;
    private final Clock clock;

    public DetermineRefundUseCase(RefundBasisReader basis, CancellationPolicyReader policy, RefundAuthorizationRepository refunds, RefundAutomation automation,
            Clock clock) {
        this.basis = basis;
        this.policy = policy;
        this.refunds = refunds;
        this.automation = automation;
        this.clock = clock;
    }

    @Override
    public RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Integer affectedTravelers, Instant occurredAt) {
        if (origin != RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER && origin != RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION)
            throw new DomainException("origin", "origin is not a refund determined without a decision");
        boolean emergency = origin == RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION;
        if (emergency && (affectedTravelers == null || affectedTravelers < 1))
            throw new DomainException("affectedTravelers", "affectedTravelers is required, 1 or more");
        if (!emergency && affectedTravelers != null) throw new DomainException("affectedTravelers", "affectedTravelers is only for a personal emergency");
        RefundBasis reservation = basis.find(tenantId, reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Optional<RefundAuthorization> stored = refunds.findByReservationAndOrigin(tenantId, reservationId, origin);
        if (stored.isPresent()) return stored.get();
        Cents amount = emergency ? emergencyRefund(tenantId, reservation, affectedTravelers, occurredAt) : wholeValue(reservation);
        return automation.determine(tenantId, reservationId, origin, amount);
    }

    private static Cents wholeValue(RefundBasis reservation) {
        if (reservation.validated().amount() <= 0) throw new NoRefundDueException("the reservation has no paid and validated value to refund");
        return reservation.validated();
    }

    private Cents emergencyRefund(UUID tenantId, RefundBasis reservation, int affectedTravelers, Instant occurredAt) {
        Instant at = occurredAt == null ? Instant.now(clock) : occurredAt;
        OptionalInt points = policy.penaltyBasisPoints(tenantId, reservation.serviceId(), reservation.departureStart(), at);
        if (points.isEmpty()) throw new NoRefundDueException("the policy of the service establishes no refund for a personal emergency");
        Cents refund = RefundCalculation.of(reservation.validated(), reservation.people(), affectedTravelers, points.getAsInt()).refund();
        if (refund.amount() <= 0) throw new NoRefundDueException("the policy keeps the whole share of the affected travelers");
        return refund;
    }
}
