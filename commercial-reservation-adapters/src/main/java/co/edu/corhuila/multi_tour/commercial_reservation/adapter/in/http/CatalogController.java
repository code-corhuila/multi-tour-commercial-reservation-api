package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListCatalogServicesForManagement;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ListCatalogServicesForManagement management;
    private final TransactionalCatalogUpdate updates;

    public CatalogController(GetCatalogService get, IdempotentCatalogRegistration registration,
            ListCatalogServicesForManagement management, TransactionalCatalogUpdate updates) {
        this.get = get;
        this.registration = registration;
        this.management = management;
        this.updates = updates;
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

    @GetMapping
    public CatalogPageResponse list(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        requireTenant(principal);
        if (!principal.roles().contains("TENANT_ADMINISTRATOR")
                && !principal.roles().contains("OPERATIONAL_COLLABORATOR")) forbid();
        for (String field : query.keySet()) {
            if (!Set.of("page", "limit", "catalogStatus", "serviceType").contains(field)) {
                throw new DomainException(field, "unknown query parameter");
            }
        }
        int page = integer(query, "page", 1, Integer.MAX_VALUE);
        int limit = integer(query, "limit", 20, 100);
        var result = management.list(principal.tenantId(), enumeration(query, "catalogStatus", CatalogStatus.class),
                enumeration(query, "serviceType", CatalogServiceType.class), page, limit);
        return CatalogPageResponse.from(result, page, limit);
    }

    @PatchMapping("/{serviceId}")
    public CatalogServiceResponse update(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("serviceId") UUID serviceId, @RequestBody UpdateCatalogServiceRequest request) {
        requireTenant(principal);
        if (!principal.roles().contains("TENANT_ADMINISTRATOR")) forbid();
        return CatalogServiceResponse.from(updates.update(principal.tenantId(), serviceId, request));
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

    private static <E extends Enum<E>> Optional<E> enumeration(Map<String, String> query, String field, Class<E> type) {
        try {
            return query.containsKey(field) ? Optional.of(Enum.valueOf(type, query.get(field))) : Optional.empty();
        } catch (IllegalArgumentException error) {
            throw new DomainException(field, "must be a supported value");
        }
    }

    private static void requireTenant(AuthenticatedPrincipal principal) {
        if (principal.tenantId() == null) forbid();
    }

    private static void forbid() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
