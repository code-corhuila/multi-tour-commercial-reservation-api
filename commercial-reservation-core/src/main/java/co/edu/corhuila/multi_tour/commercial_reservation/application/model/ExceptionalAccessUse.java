package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// One use of the token of an exceptional or emergency access (HU-SEC-007 G-SEC9-1): the authorization, the Platform Administrator (the sub of the token), the tenant, the access kind, the area, the method, the route
// template (identifiers replaced), the instant and the correlation of the request. The instant is written as usedAt in the payload and as occurred_at in the envelope, the same value. The payload of the event PlatformExceptionalAccessUsed is closed to eight keys and keeps the access kind out of it.
public record ExceptionalAccessUse(UUID authorizationId, UUID platformAdministratorId, UUID tenantId, String access, String area, String method, String routeTemplate,
        Instant occurredAt, String correlationId) {

    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authorizationId", authorizationId.toString());
        payload.put("platformAdministratorId", platformAdministratorId.toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("method", method);
        payload.put("route", routeTemplate);
        payload.put("area", area);
        payload.put("usedAt", occurredAt.toString());
        payload.put("correlationId", correlationId);
        return payload;
    }
}
