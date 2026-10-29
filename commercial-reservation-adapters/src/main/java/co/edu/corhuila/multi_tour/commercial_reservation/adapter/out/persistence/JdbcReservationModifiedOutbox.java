package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationModified;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

// Joins the transaction already open on the connection, so the event commits or rolls back with the discount.
public final class JdbcReservationModifiedOutbox implements ReservationModifiedOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcReservationModifiedOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(ReservationModified event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("previousSubtotalCents", event.previousSubtotal().amount());
        payload.put("newSubtotalCents", event.newSubtotal().amount());
        payload.put("reason", event.reason());
        payload.put("authorizerId", event.authorizerId().toString());
        payload.put("order", event.order());
        try {
            jdbc.update("INSERT INTO commercial_reservation.outbox_event "
                    + "(tenant_id, event_type, reservation_id, actor_id, occurred_at, payload) "
                    + "VALUES (?, 'ReservationModified', ?, ?, ?, ?::jsonb)", event.tenantId(), event.reservationId(),
                    event.authorizerId(), event.occurredAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                    json.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload cannot be written", failure);
        }
    }
}
