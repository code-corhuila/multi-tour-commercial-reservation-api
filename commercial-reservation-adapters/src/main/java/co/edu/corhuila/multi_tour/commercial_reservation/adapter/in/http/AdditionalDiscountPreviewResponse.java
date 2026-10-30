package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;

// Schema AdditionalDiscountPreview of the contract: the breakdown the reservation would have, the value before and after, and
// what was already paid above the new value.
public record AdditionalDiscountPreviewResponse(DiscountBreakdownResponse breakdown, long previousFinalValueCents,
        long newFinalValueCents, long excessCents) {

    // The preview has no reason yet, so its additional line is named by a fixed label (the registration names it by the reason).
    static final String LABEL = "Additional discount";

    static AdditionalDiscountPreviewResponse from(Reservation reservation, RegisterAdditionalDiscount.Preview preview) {
        return new AdditionalDiscountPreviewResponse(
                DiscountBreakdownResponse.forReservation(reservation, preview.line(), LABEL),
                preview.line().base().amount(), preview.line().subtotal().amount(), preview.excess().amount());
    }
}
