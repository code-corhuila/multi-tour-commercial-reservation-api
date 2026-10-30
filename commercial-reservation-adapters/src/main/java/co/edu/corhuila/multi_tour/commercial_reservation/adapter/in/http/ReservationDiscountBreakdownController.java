package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservationDiscountBreakdown;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Contract getReservationDiscountBreakdown (HU-COM-004, PROPOSED): the same roles as the reservation read (ReservationHttpAccess),
// a Final Customer only their own reservation. The tenant and the account are the token's; a foreign reservation is a 404.
@RestController
@RequestMapping("/api/v1/commercial-reservation/reservations/{reservationId}/discount-breakdown")
public final class ReservationDiscountBreakdownController {
    private final GetReservationDiscountBreakdown breakdown;

    public ReservationDiscountBreakdownController(GetReservationDiscountBreakdown breakdown) {
        this.breakdown = breakdown;
    }

    @GetMapping
    public ReservationDiscountBreakdownResponse read(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId) {
        var actor = CustomerHttpAccess.actor(principal);
        return ReservationDiscountBreakdownResponse.from(actor.role() == CustomerActor.Role.FINAL_CUSTOMER
                ? breakdown.getOwn(actor.tenantId(), actor.actorId(), reservationId)
                : breakdown.get(actor.tenantId(), reservationId));
    }
}
