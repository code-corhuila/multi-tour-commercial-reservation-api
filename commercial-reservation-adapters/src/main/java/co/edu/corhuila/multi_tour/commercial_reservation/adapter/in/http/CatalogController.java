package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/catalog/services")
public final class CatalogController {
    private final GetCatalogService get;
    private final IdempotentCatalogRegistration registration;

    public CatalogController(GetCatalogService get, IdempotentCatalogRegistration registration) {
        this.get = get;
        this.registration = registration;
    }

    @PostMapping
    public ResponseEntity<CatalogServiceResponse> create(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CreateCatalogServiceRequest request) {
        requireTenant(principal);
        if (!principal.roles().contains("TENANT_ADMINISTRATOR")) forbid();
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var command = new RegisterCatalogService.Command(principal.tenantId(), request.serviceType(),
                request.commercialName(), request.baseRateCents(), request.baseOperationalCostCents(),
                request.configuredCapacity(), request.transportRequired(), request.highRisk());
        var result = registration.register(command, key);
        var body = CatalogServiceResponse.from(result.item());
        return result.created() ? ResponseEntity.created(URI.create("/api/v1/catalog/services/" + body.serviceId()))
                .body(body) : ResponseEntity.ok(body);
    }

    @GetMapping("/{serviceId}")
    public CatalogServiceResponse read(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("serviceId") UUID serviceId) {
        requireTenant(principal);
        if (!principal.roles().contains("TENANT_ADMINISTRATOR")
                && !principal.roles().contains("OPERATIONAL_COLLABORATOR")) forbid();
        return CatalogServiceResponse.from(get.get(principal.tenantId(), serviceId));
    }

    private static void requireTenant(AuthenticatedPrincipal principal) {
        if (principal.tenantId() == null) forbid();
    }

    private static void forbid() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
