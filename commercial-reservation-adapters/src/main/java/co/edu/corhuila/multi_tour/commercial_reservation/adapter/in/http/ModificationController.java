package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentModification;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteModification;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

// The routes of HU-COM-012 (PROPOSED in the contract): the quote, the modification and its history. The user and the tenant are the verified
// token and the instant is the clock, never the body; the role gate is ModificationHttpAccess and the use case checks it again before
// anything is read. The reason is stored and returned, never logged.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class ModificationController {
    private final QuoteModification quote;
    private final ListReservationModifications history;
    private final GetReservation reservations;
    private final IdempotentModification writes;
    private final Clock clock;

    public ModificationController(QuoteModification quote, ListReservationModifications history, GetReservation reservations,
            IdempotentModification writes, Clock clock) {
        this.quote = quote;
        this.history = history;
        this.reservations = reservations;
        this.writes = writes;
        this.clock = clock;
    }

    @PostMapping("/reservations/{reservationId}/modification-quote")
    public ModificationQuoteResponse quote(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestBody ModificationHttpRequest body) {
        var actor = PaymentActors.of(principal);
        var request = body.toRequest(false, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        var answer = quote.quote(actor, reservationId, request);
        return ModificationQuoteResponse.of(answer, request.changeType(), reservations.get(actor.tenantId(), reservationId));
    }

    @PostMapping("/reservations/{reservationId}/modifications")
    public ResponseEntity<ModificationRecordResponse> modify(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ModificationHttpRequest body) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        var actor = PaymentActors.of(principal);
        var request = body.toRequest(true, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        var done = writes.modify(actor, reservationId, request, body.reason(), key);
        var response = ModificationRecordResponse.of(done.record(), reservations.get(actor.tenantId(), reservationId));
        if (!done.created()) return ResponseEntity.ok(response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/commercial-reservation/reservations/" + reservationId + "/modifications")).body(response);
    }

    @GetMapping("/reservations/{reservationId}/modifications")
    public ReservationListResponses<ModificationRecordResponse> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestParam Map<String, String> query) {
        var actor = PaymentActors.of(principal);
        var paging = new PageQuery(query, Set.of("page", "limit"));
        var page = history.list(actor, reservationId, paging.page, paging.limit);
        var reservation = reservations.get(actor.tenantId(), reservationId);
        return new ReservationListResponses<>(page.items().stream().map(row -> ModificationRecordResponse.of(row, reservation)).toList(),
                paging.meta(page.total()));
    }
}
