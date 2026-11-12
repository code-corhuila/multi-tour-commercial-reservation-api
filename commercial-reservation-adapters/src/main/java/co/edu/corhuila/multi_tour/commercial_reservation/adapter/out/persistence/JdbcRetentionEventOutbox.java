package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019 plan D-06 (-db 033): SensitiveDataDeleted over the outbox, with the closed key list of the db and no actor. The reservation is the aggregate and the customer travels
// in the payload; the caller opens the transaction, so the event commits or rolls back with the deletion.
public final class JdbcRetentionEventOutbox implements RetentionEventOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcRetentionEventOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(SensitiveDataDeletion deletion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", deletion.reservationId().toString());
        payload.put("customerId", deletion.customerId().toString());
        payload.put("anchorBasis", deletion.basis().name());
        payload.put("anchorAt", deletion.anchorAt().toString());
        payload.put("customerDataDeleted", deletion.customerDataDeleted());
        payload.put("companionRowsDeleted", deletion.companionRowsDeleted());
        try {
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, reservation_id, actor_id, occurred_at, payload) "
                    + "VALUES (?, ?, 'SensitiveDataDeleted', ?, NULL, ?, ?::jsonb)", UUID.randomUUID(), deletion.tenantId(), deletion.reservationId(),
                    deletion.deletedAt().atOffset(ZoneOffset.UTC), JSON.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload cannot be written", failure);
        }
    }
}
