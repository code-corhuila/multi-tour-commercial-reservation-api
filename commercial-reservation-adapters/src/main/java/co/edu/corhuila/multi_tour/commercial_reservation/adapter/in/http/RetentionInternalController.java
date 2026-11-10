package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DuePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DeleteSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDueSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// HU-COM-019 plan D-05, DP-05 (PROPOSED routes): the due list and the deletion for the retention job. The SERVICE role and nothing else; the tenant is the path because a service
// token has none, so the filter lets an inactive tenant through. A reservation that is not due is an answer, not an error; the body of an answer holds no datum.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class RetentionInternalController {
    public record DueRowResponse(UUID reservationId, UUID customerId, String anchorBasis, String anchorAt) {}
    public record DueResponse(List<DueRowResponse> data, UUID next) {}
    public record DeletionResponse(String outcome, int companionRowsDeleted, boolean customerDataDeleted) {}

    private final ListDueSensitiveData list;
    private final DeleteSensitiveData deletion;

    public RetentionInternalController(ListDueSensitiveData list, DeleteSensitiveData deletion) {
        this.list = list;
        this.deletion = deletion;
    }

    @GetMapping("/sensitive-data-retention/due")
    public DueResponse due(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "limit", defaultValue = "20") int limit, @RequestParam(value = "after", required = false) String after) {
        requireService(principal);
        if (limit < 1 || limit > 100) throw new DomainException("limit", "limit must be between 1 and 100");
        DuePage page = list.due(tenantId, limit, after == null ? null : uuid(after, "after"));
        return new DueResponse(page.rows().stream().map(row -> new DueRowResponse(row.reservationId(), row.customerId(), row.basis().name(), row.anchorAt().toString())).toList(),
                page.next());
    }

    @PostMapping("/reservations/{reservationId}/sensitive-data-deletion")
    public DeletionResponse delete(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("reservationId") UUID reservationId, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        requireService(principal);
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        DeletionResult result = deletion.delete(tenantId, reservationId);
        return new DeletionResponse(result.outcome().name(), result.companionRows(), result.customerDataDeleted());
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(field, field + " must be a UUID");
        }
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
