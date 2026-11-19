package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListInternalDepartureReservations;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// listInternalDepartureReservations (PROPOSED in the contract, G-API6-1): the reservations of a departure for operations-cost. The SERVICE role and nothing else,
// the tenant from the path because a service token has none. The only customer field is the id of the lead customer; the answer is never logged.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class InternalDepartureController {
    public record Row(UUID reservationId, String status, int travelers, UUID customerId, long validatedAmountCents) {}

    private final ListInternalDepartureReservations reservations;

    public InternalDepartureController(ListInternalDepartureReservations reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/departures/{departureId}/reservations")
    public ReservationListResponses<Row> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("departureId") UUID departureId, @RequestParam Map<String, String> query) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var paging = new PageQuery(query, Set.of("page", "limit"));
        var page = reservations.list(tenantId, departureId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(item -> new Row(item.reservationId(), item.status().name(), item.travelers(),
                item.customerId(), item.validatedAmountCents())).toList(), paging.meta(page.total()));
    }
}
