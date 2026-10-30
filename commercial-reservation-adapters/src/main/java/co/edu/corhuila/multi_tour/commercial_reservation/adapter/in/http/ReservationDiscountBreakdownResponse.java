package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationDiscountBreakdown;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

// Schema ReservationDiscountBreakdown of the contract. Without an additional discount only `breakdown` is present; with one, the
// values before and after, the excess and who registered it and when.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationDiscountBreakdownResponse(DiscountBreakdownResponse breakdown, Long previousFinalValueCents,
        Long newFinalValueCents, Long excessCents, Additional additionalDiscount) {

    public record Additional(UUID authorId, Instant registeredAt) {}

    static ReservationDiscountBreakdownResponse from(GetReservationDiscountBreakdown.View view) {
        var reservation = view.reservation();
        if (view.additional().isEmpty()) {
            return new ReservationDiscountBreakdownResponse(DiscountBreakdownResponse.forFixed(reservation), null, null, null, null);
        }
        var additional = view.additional().get();
        return new ReservationDiscountBreakdownResponse(
                DiscountBreakdownResponse.forReservation(reservation, additional.line(), additional.reason()),
                reservation.finalValue().amount(), additional.line().subtotal().amount(), view.excess().amount(),
                new Additional(additional.authorizerId(), additional.registeredAt()));
    }
}
