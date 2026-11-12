package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExceptionalAccessUse;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExceptionalAccessAudit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-SEC-007 G-SEC9-1; -db 040: PlatformExceptionalAccessUsed, one outbox row per use. The aggregate is the authorization, the actor is the Platform Administrator and the payload is closed to eight keys
// (ids, method, route template, area, instant, correlation): never data of the request or of the response. The correlation column keeps at most 64 characters.
public final class JdbcExceptionalAccessAudit implements ExceptionalAccessAudit {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcExceptionalAccessAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void used(ExceptionalAccessUse use) {
        String correlation = use.correlationId() == null || use.correlationId().length() <= 64 ? use.correlationId() : use.correlationId().substring(0, 64);
        try {
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, authorization_id, actor_id, occurred_at, correlation_id, payload) "
                    + "VALUES (?, 'PlatformExceptionalAccessUsed', ?, ?, ?, ?, ?::jsonb)", use.tenantId(), use.authorizationId(), use.platformAdministratorId(),
                    Timestamp.from(use.occurredAt()), correlation, JSON.writeValueAsString(use.payload()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the PlatformExceptionalAccessUsed payload cannot be written", e);
        }
    }
}
