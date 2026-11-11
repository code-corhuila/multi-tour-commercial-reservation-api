package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.server.ResponseStatusException;

// HU-COM-018 internal routes (PROPOSED in the contract, DP-06, DP-09, DP-WRK5-1): the saga of the cancelled departure opens one episode per reservation and voids the proposals of a cancelled
// proposed departure; job 5 lists what is due across tenants and asks the expiry of each episode. The SERVICE role and nothing else, the tenant from the path (a service token has none),
// never routed by the gateway. The service decides: a repeat answers 200 and changes nothing, an expiry with nothing due answers 422. The freeze and the resume wait for HU-COM-020.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal")
public final class ReschedulingInternalController {
    public record OpenRequest(UUID reservationId, UUID departureId, Instant cancelledAt) {}
    public record VoidRequest(UUID departureId, Instant cancelledAt) {}
    public record VoidResult(int voidedProposals, List<UUID> reschedulingIds) {}
    public record DueItem(UUID tenantId, UUID reservationId, UUID reschedulingId) {}

    private final OpenRescheduling opening;
    private final VoidReschedulingProposals voiding;
    private final ConcludeRescheduling conclusion;
    private final ReschedulingRepository episodes;
    private final ReschedulingQueries queries;
    private final Clock clock;

    public ReschedulingInternalController(OpenRescheduling opening, VoidReschedulingProposals voiding, ConcludeRescheduling conclusion, ReschedulingRepository episodes,
            ReschedulingQueries queries, Clock clock) {
        this.opening = opening;
        this.voiding = voiding;
        this.conclusion = conclusion;
        this.episodes = episodes;
        this.queries = queries;
        this.clock = clock;
    }

    @PostMapping("/tenants/{tenantId}/reschedulings")
    public ResponseEntity<ReschedulingResponse> open(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody OpenRequest body) {
        requireService(principal);
        requireKey(key);
        if (body.reservationId() == null) throw new DomainException("reservationId", "reservationId is required");
        if (body.departureId() == null) throw new DomainException("departureId", "departureId is required");
        if (body.cancelledAt() == null) throw new DomainException("cancelledAt", "cancelledAt is required");
        boolean existed = episodes.findByIncident(tenantId, body.reservationId(), body.departureId()).isPresent();
        var episode = opening.open(new OpenReschedulingCommand(tenantId, body.reservationId(), body.departureId(), body.cancelledAt()));
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).body(ReschedulingResponse.of(episode));
    }

    @PostMapping("/tenants/{tenantId}/reschedulings/proposed-departure-cancellations")
    public VoidResult voidProposals(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody VoidRequest body) {
        requireService(principal);
        requireKey(key);
        if (body.departureId() == null) throw new DomainException("departureId", "departureId is required");
        if (body.cancelledAt() == null) throw new DomainException("cancelledAt", "cancelledAt is required");
        List<UUID> changed = voiding.voidProposals(tenantId, body.departureId(), body.cancelledAt());
        return new VoidResult(changed.size(), changed);
    }

    @PostMapping("/tenants/{tenantId}/reschedulings/{reschedulingId}/expiry")
    public ReschedulingResponse expire(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("reschedulingId") UUID reschedulingId) {
        requireService(principal);
        return ReschedulingResponse.of(conclusion.expire(tenantId, reschedulingId));
    }

    // DP-WRK5-1: across tenants because the worker carries none; ids only. The worker asks again while a batch comes back full, so there is no page and the total is the batch.
    @GetMapping("/reschedulings")
    public ReservationListResponses<DueItem> due(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("due", "limit"));
        if (!"true".equals(query.get("due"))) throw new DomainException("due", "due must be true");
        var items = queries.due(clock.instant(), paging.limit).stream().map(d -> new DueItem(d.tenantId(), d.reservationId(), d.episodeId())).toList();
        return new ReservationListResponses<>(items, paging.meta(items.size()));
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
