package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11): the payload of PlatformExceptionalAccessUsed is closed to eight keys; the access kind stays out of it, and nothing of the request or the response can be in it.
class ExceptionalAccessUseTest {
    @Test
    void thePayloadHasExactlyTheEightKeysOfTheContract() {
        UUID authorization = UUID.randomUUID(), administrator = UUID.randomUUID(), tenant = UUID.randomUUID();
        var use = new ExceptionalAccessUse(authorization, administrator, tenant, "emergency", "CASH", "GET", "/api/v1/x/{id}", Instant.parse("2026-11-11T10:00:00Z"), "c-1");

        var payload = use.payload();

        assertThat(payload.keySet()).containsExactlyInAnyOrderElementsOf(List.of("authorizationId", "platformAdministratorId", "tenantId", "method", "route", "area", "occurredAt", "correlationId"));
        assertThat(payload).containsEntry("authorizationId", authorization.toString()).containsEntry("platformAdministratorId", administrator.toString())
                .containsEntry("tenantId", tenant.toString()).containsEntry("method", "GET").containsEntry("route", "/api/v1/x/{id}")
                .containsEntry("area", "CASH").containsEntry("occurredAt", "2026-11-11T10:00:00Z").containsEntry("correlationId", "c-1");
    }
}
