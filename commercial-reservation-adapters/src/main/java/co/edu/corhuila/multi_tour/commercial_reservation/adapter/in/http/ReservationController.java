package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetOwnReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListOwnReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationDepartures;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservations;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The five routes of the Reservations tag (PROPOSED in the contract). There is no delete and no 409: a reservation changes state.
// The role is checked before the body is bound (ReservationHttpAccess); the tenant is only the token's.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class ReservationController {
    private static final String RESERVATIONS = "/api/v1/commercial-reservation/reservations/";
    private final ListReservationDepartures departures;
    private final QuoteReservation quote;
    private final IdempotentReservationCreation creation;
    private final ListReservations list;
    private final GetReservation get;
    private final ListOwnReservations listOwn;
    private final GetOwnReservation getOwn;
    private final Clock clock;

    public ReservationController(ListReservationDepartures departures, QuoteReservation quote,
            IdempotentReservationCreation creation, ListReservations list, GetReservation get, ListOwnReservations listOwn,
            GetOwnReservation getOwn, Clock clock) {
        this.departures = departures;
        this.quote = quote;
        this.creation = creation;
        this.list = list;
        this.get = get;
        this.listOwn = listOwn;
        this.getOwn = getOwn;
        this.clock = clock;
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

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> create(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CreateReservationRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var actor = CustomerHttpAccess.actor(principal);
        var today = LocalDate.now(clock);
        var command = isFinalCustomer(actor) ? request.toOwnCommand(actor.tenantId(), actor.actorId(), today)
                : request.toCommand(actor.tenantId(), actor.actorId(), today);
        var result = creation.create(command, key);
        var body = ReservationResponse.from(result.reservation());
        return result.created() ? ResponseEntity.created(URI.create(RESERVATIONS + body.reservationId())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/reservations")
    public ReservationListResponses<ReservationListResponses.Summary> list(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        requireOnly(query, Set.of("page", "limit", "scope"));
        String scope = query.getOrDefault("scope", "active");
        if (!Set.of("active", "all").contains(scope)) throw new DomainException("scope", "must be active or all");
        int page = integer(query, "page", 1, Integer.MAX_VALUE), limit = integer(query, "limit", 20, 100);
        var actor = CustomerHttpAccess.actor(principal);
        var found = isFinalCustomer(actor) ? listOwn.list(actor.tenantId(), actor.actorId(), scope.equals("all"), page, limit)
                : list.list(actor.tenantId(), scope.equals("all"), page, limit);
        return ReservationListResponses.of(found, page, limit);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId) {
        var actor = CustomerHttpAccess.actor(principal);
        return ReservationResponse.from(isFinalCustomer(actor) ? getOwn.get(actor.tenantId(), actor.actorId(), reservationId)
                : get.get(actor.tenantId(), reservationId));
    }

    // A Final Customer is always served by the own-scope use cases: their token never reaches the tenant-wide reads (RN-08).
    private static boolean isFinalCustomer(CustomerActor actor) {
        return actor.role() == CustomerActor.Role.FINAL_CUSTOMER;
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
