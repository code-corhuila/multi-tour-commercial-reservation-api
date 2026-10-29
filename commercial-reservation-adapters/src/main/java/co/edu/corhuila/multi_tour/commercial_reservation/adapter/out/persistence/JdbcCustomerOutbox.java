package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SensitiveDataAccessOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataAccessed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Writes inside the transaction of the caller. The payload holds references only (RN-13): never a customer value.
public final class JdbcCustomerOutbox implements CustomerRegisteredOutbox, SensitiveDataAccessOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcCustomerOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(CustomerRegistered event) {
        insert("CustomerRegistered", event.tenantId(), event.customerId(), event.actorId(), event.occurredAt(),
                payload(event.tenantId(), event.customerId(), event.actorId(), event.occurredAt(), null));
    }

    @Override
    public void append(SensitiveDataAccessed event) {
        insert("SensitiveDataAccessed", event.tenantId(), event.customerId(), event.actorId(), event.occurredAt(),
                payload(event.tenantId(), event.customerId(), event.actorId(), event.occurredAt(), event.reason()));
    }

    private void insert(String type, UUID tenant, UUID customer, UUID actor, Instant occurredAt, String payload) {
        jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, customer_id, actor_id, "
                + "occurred_at, payload) VALUES (?, ?, ?, ?, ?, ?::jsonb)", tenant, type, customer, actor,
                occurredAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC), payload);
    }

    private static String payload(UUID tenant, UUID customer, UUID actor, Instant occurredAt, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenant);
        body.put("customerId", customer);
        body.put("actorId", actor);
        body.put("occurredAt", occurredAt.truncatedTo(ChronoUnit.MICROS).toString());
        if (reason != null) body.put("reason", reason);
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload could not be written");
        }
    }
}
