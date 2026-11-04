package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RestController;

// The routes of HU-COM-013 (PROPOSED in the contract): the quote, the cancellation at the customer's request and its record. The user
// and the tenant are the verified token and the instant is the clock, never the body; the role gate is CancellationHttpAccess and the
// use case checks it again before anything is read.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class CancellationController {
    public record CancelRequest(String reason, String origin) {}

    private final QuoteCancellation quote;
    private final GetCancellationRecord record;
    private final IdempotentCancellation writes;

    public CancellationController(QuoteCancellation quote, GetCancellationRecord record, IdempotentCancellation writes) {
        this.quote = quote;
        this.record = record;
        this.writes = writes;
    }

    @GetMapping("/reservations/{reservationId}/cancellation-quote")
    public CancellationQuoteResponse quote(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId) {
        return CancellationQuoteResponse.of(quote.quote(PaymentActors.of(principal), reservationId));
    }

    @PostMapping("/reservations/{reservationId}/cancellation")
    public ResponseEntity<CancellationResponse> cancel(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CancelRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        var done = writes.cancel(new CancelReservationCommand(PaymentActors.of(principal), reservationId, request.reason(), request.origin()), key);
        var body = CancellationResponse.of(done.record());
        if (!done.created()) return ResponseEntity.ok(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/commercial-reservation/reservations/" + reservationId + "/cancellation")).body(body);
    }

    @GetMapping("/reservations/{reservationId}/cancellation")
    public CancellationResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId) {
        return CancellationResponse.of(record.get(PaymentActors.of(principal), reservationId));
    }
}
