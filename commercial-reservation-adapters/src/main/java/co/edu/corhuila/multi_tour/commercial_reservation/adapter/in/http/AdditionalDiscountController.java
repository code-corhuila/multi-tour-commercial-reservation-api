package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

// Contract previewAdditionalDiscount and registerAdditionalDiscount (HU-COM-004, PROPOSED). Tenant Administrator only: the
// access interceptor runs before the query or the body is read. The tenant and the author are the token's, never the request's.
@RestController
@RequestMapping("/api/v1/commercial-reservation/reservations/{reservationId}/additional-discount")
public final class AdditionalDiscountController {
    // Contract: the percentage is at least 100 basis points (1 %) and at most 10000; there is no cap (decision 2026-10-28).
    private static final int MIN = 100, MAX = 10_000;
    private final RegisterAdditionalDiscount discounts;
    private final GetReservation reservations;
    private final IdempotentAdditionalDiscountRegistration registration;

    public AdditionalDiscountController(RegisterAdditionalDiscount discounts, GetReservation reservations,
            IdempotentAdditionalDiscountRegistration registration) {
        this.discounts = discounts;
        this.reservations = reservations;
        this.registration = registration;
    }

    // 201 with Location the first time, 200 with the same breakdown on a repeat of the key (AC-04.11). The Location is the
    // additional discount of the reservation: DEFAULT, the contract names the header but the resource has no id of its own.
    @PostMapping
    public ResponseEntity<DiscountBreakdownResponse> register(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody RegisterAdditionalDiscountRequest request) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        }
        var actor = DiscountRuleHttpAccess.actor(principal);
        var result = registration.register(actor, request.toCommand(reservationId), key);
        var body = DiscountBreakdownResponse.forReservation(reservations.get(actor.tenantId(), reservationId),
                result.discount().line(), result.discount().reason());
        return result.created() ? ResponseEntity.created(URI.create("/api/v1/commercial-reservation/reservations/"
                + reservationId + "/additional-discount")).body(body) : ResponseEntity.ok(body);
    }

    @GetMapping("/preview")
    public AdditionalDiscountPreviewResponse preview(
            @RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("reservationId") UUID reservationId, @RequestParam Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("basisPoints").contains(field)) throw new DomainException(field, "unknown query parameter");
        }
        var actor = DiscountRuleHttpAccess.actor(principal);
        var preview = discounts.previewWithExcess(actor, reservationId, basisPoints(query.get("basisPoints")));
        return AdditionalDiscountPreviewResponse.from(reservations.get(actor.tenantId(), reservationId), preview);
    }

    static int basisPoints(String value) {
        if (value == null || value.isBlank()) throw new DomainException("basisPoints", "basisPoints is required");
        try {
            return requireRange(Integer.parseInt(value));
        } catch (NumberFormatException invalid) {
            throw new DomainException("basisPoints", "must be an integer between " + MIN + " and " + MAX);
        }
    }

    static int requireRange(int basisPoints) {
        if (basisPoints < MIN || basisPoints > MAX) {
            throw new DomainException("basisPoints", "must be an integer between " + MIN + " and " + MAX);
        }
        return basisPoints;
    }
}
