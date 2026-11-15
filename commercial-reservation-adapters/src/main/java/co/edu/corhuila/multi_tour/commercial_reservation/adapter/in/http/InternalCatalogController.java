package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// getInternalCatalogService (PROPOSED in the contract, G-API8-1): the read of a catalog item for the saga change-departure-vehicles. The SERVICE role and nothing
// else, the tenant from the path because a service token has none (authentication.md); the public route keeps its roles. It reads only.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class InternalCatalogController {
    private final GetCatalogService get;

    public InternalCatalogController(GetCatalogService get) {
        this.get = get;
    }

    @GetMapping("/catalog/services/{serviceId}")
    public CatalogServiceResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("serviceId") UUID serviceId) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return CatalogServiceResponse.from(get.get(tenantId, serviceId));
    }
}
