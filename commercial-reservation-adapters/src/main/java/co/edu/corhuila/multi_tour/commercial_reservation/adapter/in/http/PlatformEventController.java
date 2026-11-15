package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountSessionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyAccountSessionsRevoked;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionsRevoked;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Operation receivePlatformEvent of the contract (api spec 022): the route without tenant where job 7 delivers AccountSessionsRevoked, because the
// account of a Platform Administrator has no tenant. It only maps the body and answers 201 or 200; the rules live in the use case. The body is read
// as a tree because the contract tolerates fields it does not name. payload.tenantId is optional and never required.
@RestController
public final class PlatformEventController {
    private static final int MAX_BYTES = 16 * 1024;
    private static final String EVENT_TYPE = "AccountSessionsRevoked";

    private final ApplyAccountSessionsRevoked apply;
    private final ObjectMapper json;

    public PlatformEventController(ApplyAccountSessionsRevoked apply, ObjectMapper json) {
        this.apply = apply;
        this.json = json;
    }

    @PostMapping("/api/v1/commercial-reservation/internal/events")
    public ResponseEntity<Void> receive(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
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
        JsonNode body = read(raw);
        if (!EVENT_TYPE.equals(body.path("eventType").asText(null))) {
            throw new DomainException("eventType", "is not an event this route receives");
        }
        UUID eventId = uuid(body, "eventId");
        if (key == null || key.length() < 8 || key.length() > 128 || !key.equalsIgnoreCase(eventId.toString())) {
            throw new DomainException("Idempotency-Key", "must be the eventId of the body");
        }
        JsonNode version = body.path("eventVersion");
        if (!version.isIntegralNumber() || version.asInt() < 1) throw new DomainException("eventVersion", "must be an integer of 1 or more");
        JsonNode payload = body.path("payload");
        if (!payload.isObject()) throw new DomainException("payload", "is required");
        UUID accountId = uuid(payload, "accountId");
        if (!uuid(body, "aggregateId").equals(accountId)) throw new DomainException("aggregateId", "must be the account of the payload");
        AccountSessionOutcome outcome = apply.apply(new AccountSessionsRevoked(eventId, accountId, optionalUuid(payload, "tenantId"),
                instant(payload, "revokedFrom")));
        return ResponseEntity.status(outcome == AccountSessionOutcome.APPLIED ? HttpStatus.CREATED : HttpStatus.OK).build();
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

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (DateTimeParseException invalid) {
            throw new DomainException(field, "must be an ISO 8601 UTC timestamp");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new DomainException(field, "is required");
        return value.textValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(field, "must be a UUID");
        }
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : uuid(node, field);
    }
}
