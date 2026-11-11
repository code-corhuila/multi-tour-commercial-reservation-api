package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcExceptionalAccessAudit;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExceptionalAccessUse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (migration 040 of the -db repo): each use of the exceptional access is one row of the outbox, with the authorization as aggregate, the administrator as actor and the closed payload.
// Runs against the real database only (it needs the three TEST_DATABASE variables), like the other persistence tests.
class ExceptionalAccessOutboxPersistenceTest {
    private ReservationPersistenceFixture fixture;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
    }

    @Test
    void aUseIsWrittenToTheOutboxWithTheAuthorizationAsAggregateAndTheClosedPayload() {
        UUID authorization = UUID.randomUUID();
        var use = new ExceptionalAccessUse(authorization, fixture.actor, fixture.tenant, "exceptional", "RESERVATIONS", "GET", "/api/v1/commercial-reservation/reservations/{id}",
                Instant.parse("2026-11-11T10:00:00Z"), "trace-1");

        new JdbcExceptionalAccessAudit(fixture.jdbc).used(use);

        List<Map<String, Object>> rows = fixture.jdbc.queryForList("SELECT event_type, tenant_id, authorization_id, actor_id, correlation_id, payload::text AS payload "
                + "FROM commercial_reservation.outbox_event WHERE authorization_id = ?", authorization);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("event_type")).isEqualTo("PlatformExceptionalAccessUsed");
        assertThat(row.get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(row.get("actor_id")).isEqualTo(fixture.actor);
        assertThat(row.get("correlation_id")).isEqualTo("trace-1");
        assertThat((String) row.get("payload")).contains("\"area\": \"RESERVATIONS\"").contains("\"occurredAt\": \"2026-11-11T10:00:00Z\"").doesNotContain("access");
    }
}
