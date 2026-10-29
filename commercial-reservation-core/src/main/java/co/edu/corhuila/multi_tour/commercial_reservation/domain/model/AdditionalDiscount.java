package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Immutable: one per reservation, applied after the current discounts, on their subtotal (RN-04, RN-06).
// The reason is kept as text (DP-06); the order is its place in the breakdown.
public record AdditionalDiscount(UUID tenantId, UUID reservationId, BasisPoints rate, DiscountLine line,
        String reason, UUID authorizerId, int order, Instant registeredAt) {

    // 1 % (the mockup minimum, RN-05).
    public static final int MIN_BASIS_POINTS = 100;

    // Used by both the preview and the registration, so they cannot disagree (AC-04.13).
    // TODO DP-03: the cap of the additional discount is NO DEFINIDO; no cap is applied until the owner closes it.
    public static DiscountLine preview(Cents subtotalAfterCurrentDiscounts, BasisPoints rate) {
        if (rate.value() < MIN_BASIS_POINTS) {
            throw new DomainException("percentage", "the additional discount must be at least 1 %");
        }
        return DiscountLine.percentage(subtotalAfterCurrentDiscounts, rate);
    }
}
