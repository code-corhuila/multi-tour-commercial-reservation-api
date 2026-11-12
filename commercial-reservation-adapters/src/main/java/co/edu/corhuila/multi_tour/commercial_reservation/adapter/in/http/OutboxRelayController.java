package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RelayOutboxDelivery;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The three outbox routes of job 7 (listOutboxEvents, markOutboxEventPublished, recordOutboxEventAttempt: PROPOSED in the contract, ADR-004 D4). They read
// across tenants by design, so there is no tenant in the path; the SERVICE role and nothing else, never routed by the gateway. They write only the delivery
// columns of the outbox row (RN-03); a repeat answers 200 with the state as it stands.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/outbox")
public final class OutboxRelayController {
    private static final int MAX_ERROR = 1000;

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AttemptRequest(String error) {}

    private final RelayOutboxDelivery relay;

    public OutboxRelayController(RelayOutboxDelivery relay) {
        this.relay = relay;
    }

    @GetMapping
    public ReservationListResponses<OutboxEventResponse> pending(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "status"));
        if (query.containsKey("status") && !"PENDING".equals(query.get("status"))) throw new DomainException("status", "only PENDING is read");
        var page = relay.pending(paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(OutboxEventResponse::of).toList(), paging.meta(page.total()));
    }

    @PostMapping("/{eventId}/published")
    public OutboxEventResponse published(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("eventId") UUID eventId) {
        requireService(principal);
        return OutboxEventResponse.of(relay.markPublished(eventId));
    }

    @PostMapping("/{eventId}/attempts")
    public OutboxEventResponse attempt(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("eventId") UUID eventId, @RequestBody AttemptRequest body) {
        requireService(principal);
        if (body.error() == null || body.error().isBlank() || body.error().length() > MAX_ERROR) {
            throw new DomainException("error", "error is required, 1 to " + MAX_ERROR + " characters");
        }
        return OutboxEventResponse.of(relay.recordAttempt(eventId, body.error()));
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
