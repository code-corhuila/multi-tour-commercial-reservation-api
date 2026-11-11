package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationCreated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

// Writes inside the transaction of the caller. The payload keys are the closed list of the database (-db 012): no person datum.
public final class JdbcReservationCreatedOutbox implements ReservationCreatedOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcReservationCreatedOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(ReservationCreated event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("customerId", event.customerId().toString());
        payload.put("serviceId", event.serviceId().toString());
        payload.put("projectedValueCents", event.projectedValue().amount());
        payload.put("finalValueCents", event.finalValue().amount());
        payload.put("reservationStatus", "PENDING_PAYMENT");
        payload.put("paymentStatus", "NO_PAYMENT");
        try {
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, reservation_id, actor_id, "
                    + "occurred_at, payload) VALUES (?, 'ReservationCreated', ?, ?, ?, ?::jsonb)", event.tenantId(),
                    event.reservationId(), event.actorId(),
                    event.occurredAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC), JSON.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload cannot be written", failure);
        }
    }
}
