package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.IdempotentExpiry;
import com.fasterxml.jackson.annotation.JsonInclude;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDuePaymentDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;
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

// The internal routes of job 1 (PROPOSED in the contract, AC-11.12): the SERVICE role and nothing else, the tenant from the path because
// a service token has none. The job asks and the service decides: the list names candidates, the expiry re-checks each one.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class PaymentExpiryController {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Expiry(UUID reservationId, boolean expired, String reason, String cause, String reservationStatus) {}

    private final ListDuePaymentDeadlines due;
    private final IdempotentExpiry expiry;

    public PaymentExpiryController(ListDuePaymentDeadlines due, IdempotentExpiry expiry) {
        this.due = due;
        this.expiry = expiry;
    }

    @GetMapping("/payment-deadlines")
    public ReservationListResponses<DeadlineResponse> due(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @RequestParam Map<String, String> query) {
        requireService(principal);
        var paging = new PageQuery(query, Set.of("page", "limit", "due"));
        if (!"true".equals(query.get("due"))) throw new DomainException("due", "due must be true");
        var page = due.list(tenantId, paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(DeadlineResponse::of).toList(), paging.meta(page.total()));
    }

    @PostMapping("/reservations/{reservationId}/expiry")
    public Expiry expire(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("reservationId") UUID reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        requireService(principal);
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        var result = expiry.expire(tenantId, reservationId, key);
        return new Expiry(result.reservationId(), result.expired(), result.reason() == null ? null : result.reason().name(),
                result.cause() == null ? null : result.cause().name(), result.status().name());
    }

    private static void requireService(AuthenticatedPrincipal principal) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
