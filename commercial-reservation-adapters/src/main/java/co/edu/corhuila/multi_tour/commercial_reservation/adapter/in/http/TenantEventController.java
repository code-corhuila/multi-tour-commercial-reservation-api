package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyTenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEventType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Operation receiveEvent of the contract (plan D-03): the one route job 7 posts the tenant events to. It only maps the
// body to a TenantEvent and answers 201 or 200; the rules live in the use case. The body is read as a tree because the
// contract tolerates fields it does not name, while the strict mapper of the service would refuse them.
@RestController
public final class TenantEventController {
    private static final int MAX_BYTES = 16 * 1024;

    private final ApplyTenantEvent apply;
    private final ObjectMapper json;

    public TenantEventController(ApplyTenantEvent apply, ObjectMapper json) {
        this.apply = apply;
        this.json = json;
    }

    @PostMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}/events")
    public ResponseEntity<Void> receive(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody byte[] raw) {
        if (!principal.roles().contains("SERVICE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new RejectedRequest(415, "Content-Type must be application/json");
        }
        if (raw.length > MAX_BYTES) {
            throw new RejectedRequest(413, "body must not exceed 16384 bytes");
        }
        TenantEvent event = toEvent(read(raw), tenantId);
        if (key == null || !key.equalsIgnoreCase(event.eventId().toString())) {
            throw new DomainException("Idempotency-Key", "must be the eventId of the body");
        }
        return ResponseEntity.status(apply.apply(event) == TenantEventOutcome.APPLIED ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    private JsonNode read(byte[] raw) {
        try {
            JsonNode root = json.readTree(raw);
            if (root == null || !root.isObject()) throw new DomainException("body", "must be a JSON object");
            return root;
        } catch (IOException invalid) {
            throw new DomainException("body", "must contain valid JSON");
        }
    }

    private static TenantEvent toEvent(JsonNode body, UUID pathTenant) {
        UUID eventId = uuid(body, "eventId");
        TenantEventType type = type(text(body, "eventType"));
        JsonNode version = body.path("eventVersion");
        if (!version.isIntegralNumber() || version.asInt() < 1) throw new DomainException("eventVersion", "must be an integer of 1 or more");
        UUID tenantId = uuid(body, "tenantId");
        if (!tenantId.equals(pathTenant) || !uuid(body, "aggregateId").equals(tenantId)) {
            throw new DomainException("tenantId", "must be the tenant of the path");
        }
        JsonNode payload = body.path("payload");
        if (!payload.isObject() || !uuid(payload, "tenantId").equals(tenantId)) {
            throw new DomainException("payload", "must carry the tenantId of the event");
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(text(body, "occurredAt"));
        } catch (DateTimeParseException invalid) {
            throw new DomainException("occurredAt", "must be an ISO 8601 UTC timestamp");
        }
        return new TenantEvent(eventId, type, tenantId, occurredAt, optional(payload, "identifier"),
                status(optional(payload, "tenantStatus")), optional(payload, "timeZone"), optional(payload, "country"));
    }

    private static TenantEventType type(String name) {
        return switch (name) {
            case "TenantCreated" -> TenantEventType.TENANT_CREATED;
            case "TenantInactivated" -> TenantEventType.TENANT_INACTIVATED;
            case "TenantReactivated" -> TenantEventType.TENANT_REACTIVATED;
            case "TenantLocalizationChanged" -> TenantEventType.TENANT_LOCALIZATION_CHANGED;
            default -> throw new DomainException("eventType", "is not an event this service consumes");
        };
    }

    private static TenantStatus status(String name) {
        if (name == null) return null;
        try {
            return TenantStatus.valueOf(name);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("tenantStatus", "must be ACTIVE or INACTIVE");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new DomainException(field, "is required");
        return value.textValue();
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw new DomainException(field, "must be a string");
        return value.textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(field, "must be a UUID");
        }
    }
}
