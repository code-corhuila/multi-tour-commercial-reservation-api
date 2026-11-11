package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The routes of the Reservations tag (PROPOSED in the contract). There is no delete and no 409: a reservation changes state.
// The role is checked before the body is bound (ReservationHttpAccess); the tenant is only the token's.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class ReservationController {
    private final ListReservationDepartures departures;
    private final QuoteReservation quote;

    public ReservationController(ListReservationDepartures departures, QuoteReservation quote) {
        this.departures = departures;
        this.quote = quote;
    }

    @GetMapping("/catalog/services/{serviceId}/departures")
    public ReservationListResponses<ReservationListResponses.Departure> departures(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("serviceId") UUID serviceId, @RequestParam Map<String, String> query) {
        requireOnly(query, Set.of("page", "limit"));
        int page = integer(query, "page", 1, Integer.MAX_VALUE), limit = integer(query, "limit", 20, 100);
        return ReservationListResponses.of(departures.list(principal.tenantId(), serviceId, page, limit), page, limit);
    }

    @PostMapping("/reservation-quotes")
    public ReservationQuoteResponse quote(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestBody QuoteReservationRequest request) {
        request.requireComplete();
        return ReservationQuoteResponse.from(quote.quote(principal.tenantId(), request.serviceId(), request.salidaId(), request.people()));
    }

    private static void requireOnly(Map<String, String> query, Set<String> allowed) {
        for (String field : query.keySet()) {
            if (!allowed.contains(field)) throw new DomainException(field, "unknown query parameter");
        }
    }

    private static int integer(Map<String, String> query, String field, int fallback, int maximum) {
        try {
            int value = query.containsKey(field) ? Integer.parseInt(query.get(field)) : fallback;
            if (value < 1 || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new DomainException(field, "must be an integer between 1 and " + maximum);
        }
    }
}
