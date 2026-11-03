package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ResolvePublicTenant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.EstablishmentHttpAccess.actor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.*;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.*;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public final class EstablishmentController {
    private final RegisterAssociatedEstablishment register;
    private final GetAssociatedEstablishment get;
    private final ListAssociatedEstablishments list;
    private final InactivateAssociatedEstablishment inactivate;
    private final ReactivateAssociatedEstablishment reactivate;
    private final AssociatedEstablishmentRepository repository;
    private final EstablishmentImages images;
    private final ResolvePublicTenant tenants;
    public EstablishmentController(RegisterAssociatedEstablishment register, GetAssociatedEstablishment get,
            ListAssociatedEstablishments list, InactivateAssociatedEstablishment inactivate,
            ReactivateAssociatedEstablishment reactivate, AssociatedEstablishmentRepository repository, EstablishmentImages images,
            ResolvePublicTenant tenants) {
        this.register = register; this.get = get; this.list = list; this.inactivate = inactivate;
        this.reactivate = reactivate; this.repository = repository; this.images = images; this.tenants = tenants;
    }
    @PostMapping(value = "/api/v1/commercial-reservation/associated-establishments", consumes = "multipart/form-data")
    public ResponseEntity<Response> create(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestParam(value = "establishmentType", required = false) String type,
            @RequestParam(value = "commercialName", required = false) String name,
            @RequestParam(value = "commercialInformation", required = false) String information,
            @RequestParam(value = "image", required = false) MultipartFile image, HttpServletRequest request) throws java.io.IOException {
        for (String field : request.getParameterMap().keySet()) {
            if (!Set.of("establishmentType", "commercialName", "commercialInformation", "image").contains(field))
                throw new DomainException(field, "unknown field");
        }
        if (image == null) throw new DomainException("image", "image is required");
        if (image.getSize() > 819200) throw new EstablishmentImageContent.Rejected(413, "image must not exceed 819200 bytes");
        byte[] bytes = image.getBytes();
        String mediaType = EstablishmentImageContent.type(bytes);
        String reference = UUID.randomUUID().toString();
        var result = images.register(bytes, mediaType, () -> register.register(actor(principal), key,
                new RegisterAssociatedEstablishment.Command(type, name, information, reference)));
        var body = Response.from(result.association());
        return result.created() ? ResponseEntity.created(URI.create("/api/v1/commercial-reservation/associated-establishments/" + body.associationId()))
                .body(body) : ResponseEntity.ok(body);
    }
    @GetMapping("/api/v1/commercial-reservation/associated-establishments/{associationId}")
    public Response read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("associationId") UUID id) {
        return Response.from(get.get(actor(principal), id));
    }
    @GetMapping("/api/v1/commercial-reservation/associated-establishments")
    public Map<String, Object> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("page", "limit", "associationStatus", "establishmentType").contains(field))
                throw new DomainException(field, "unknown query parameter");
        }
        int page = integer(query, "page", 1), limit = integer(query, "limit", 20);
        var result = list.list(actor(principal), enumeration(query, "associationStatus", AssociationStatus.class),
                enumeration(query, "establishmentType", EstablishmentType.class), page, limit);
        return Map.of("data", result.items().stream().map(Response::from).toList(), "meta",
                Map.of("page", page, "limit", limit, "total", result.total(), "totalPages", (result.total() + limit - 1) / limit));
    }
    @PostMapping("/api/v1/commercial-reservation/associated-establishments/{associationId}/inactivate")
    public Response inactivate(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("associationId") UUID id) { return Response.from(inactivate.inactivate(actor(principal), id)); }
    @PostMapping("/api/v1/commercial-reservation/associated-establishments/{associationId}/reactivate")
    public Response reactivate(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("associationId") UUID id) { return Response.from(reactivate.reactivate(actor(principal), id)); }
    @GetMapping("/api/v1/commercial-reservation/public/tenants/{identifier}/associated-establishments/{associationId}/image")
    public ResponseEntity<byte[]> image(@PathVariable("identifier") String identifier, @PathVariable("associationId") UUID id) {
        UUID tenant = tenants.resolve(identifier);
        var item = repository.findByTenantAndId(tenant, id).filter(value -> value.tenantId().equals(tenant))
                .orElseThrow(AssociatedEstablishmentNotFoundException::new);
        var image = images.read(tenant, item.imageReference());
        return ResponseEntity.ok().header("Cache-Control", "public, max-age=86400")
                .contentType(MediaType.parseMediaType(image.mediaType())).body(image.bytes());
    }
    private static int integer(Map<String, String> query, String field, int fallback) {
        try { return query.containsKey(field) ? Integer.parseInt(query.get(field)) : fallback; }
        catch (NumberFormatException invalid) { throw new DomainException(field, "must be an integer"); }
    }
    private static <E extends Enum<E>> Optional<E> enumeration(Map<String, String> query, String field, Class<E> type) {
        try { return query.containsKey(field) ? Optional.of(Enum.valueOf(type, query.get(field))) : Optional.empty(); }
        catch (IllegalArgumentException invalid) { throw new DomainException(field, "has an invalid value"); }
    }
    public record Response(UUID associationId, EstablishmentType establishmentType, String commercialName,
            String commercialInformation, String imageReference, AssociationStatus associationStatus) {
        static Response from(AssociatedEstablishment item) {
            return new Response(item.associationId(), item.establishmentType(), item.commercialName(),
                    item.commercialInformation(), item.imageReference(), item.associationStatus());
        }
    }
}
