package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDepartureReservations;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU-COM-012 amendment 2026-11-10 (PROPOSED in the contract): the reservations of a departure, called by operations-cost with the token of the person. The role
// and the permission are decided by the use case; the tenant is only the one of the token.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class DepartureReservationController {
    public record Row(UUID reservationId, String customerName, int people, String reservationStatus) {}

    private final ListDepartureReservations reservations;

    public DepartureReservationController(ListDepartureReservations reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/catalog/services/{serviceId}/departures/{departureId}/reservations")
    public ReservationListResponses<Row> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("serviceId") UUID serviceId, @PathVariable("departureId") UUID departureId, @RequestParam Map<String, String> query) {
        var actor = CustomerHttpAccess.actor(principal);
        var paging = new PageQuery(query, Set.of("page", "limit"));
        var page = reservations.list(actor, serviceId, departureId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(item -> new Row(item.reservationId(), item.customerName(), item.people(),
                item.reservationStatus().name())).toList(), paging.meta(page.total()));
    }
}
