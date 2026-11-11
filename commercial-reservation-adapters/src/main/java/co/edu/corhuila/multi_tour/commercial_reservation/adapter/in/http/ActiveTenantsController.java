package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListActiveTenants;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The internal route that lists the active tenants for the jobs (listActiveTenants: PROPOSED in the contract, HU-WRK-001 DP-08). It reads the local
// copy across tenants by design, so there is no tenant in the path; the SERVICE role and nothing else, never routed by the gateway. Ids only.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants")
public final class ActiveTenantsController {
    public record TenantIdResponse(UUID tenantId) {}

    private final ListActiveTenants tenants;

    public ActiveTenantsController(ListActiveTenants tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    public ReservationListResponses<TenantIdResponse> active(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "status"));
        if (query.containsKey("status") && !"ACTIVE".equals(query.get("status"))) throw new DomainException("status", "only ACTIVE is listed");
        var page = tenants.list(paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(TenantIdResponse::new).toList(), paging.meta(page.total()));
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
