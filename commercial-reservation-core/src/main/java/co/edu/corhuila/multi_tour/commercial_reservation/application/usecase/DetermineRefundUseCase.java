package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.UUID;

// HU-COM-016 RN-05, AC-16.7; DP-05, DP-15: a cause not attributable refunds 100 percent of the paid and validated value, computed here and never taken from
// the request; a personal emergency takes the amount of the policy of operations. Both are authorized at once by Multitour. A reservation of another
// tenant looks missing. Declared gap: a second determination for the same reservation and origin is not detected here (the Idempotency-Key covers retries).
public class DetermineRefundUseCase implements DetermineRefund {

    private final ReservationReader reservations;
    private final RefundAutomation automation;

    public DetermineRefundUseCase(ReservationReader reservations, RefundAutomation automation) {
        this.reservations = reservations;
        this.automation = automation;
    }

    @Override
    public RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Long amountCents) {
        ReservationSnapshot reservation = reservations.findByTenantAndId(tenantId, reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (origin == RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER) {
            if (amountCents != null) throw new DomainException("amountCents", "amountCents is not accepted for a cause not attributable to the customer");
            if (reservation.paid().amount() <= 0) throw new BusinessRuleViolation("the reservation has no paid and validated value to refund");
            return automation.determine(tenantId, reservationId, origin, reservation.paid());
        }
        if (origin != RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION) throw new DomainException("origin", "origin is not a refund determined without a decision");
        if (amountCents == null || amountCents <= 0) throw new DomainException("amountCents", "amountCents is required for a personal emergency");
        return automation.determine(tenantId, reservationId, origin, new Cents(amountCents));
    }
}
