package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParametersChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-009 DP-04 (-db 044): PaymentParametersChanged over the outbox, with the closed key list of the db. The aggregate is the changed row of payment_condition, read in the same statement
// so the event can only name a row that exists; the caller opens the transaction, so the event commits or rolls back with the change. No value of a parameter and no reason travels.
public final class JdbcPaymentParametersChangeOutbox implements PaymentParametersChangeOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcPaymentParametersChangeOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(PaymentParametersChange change) {
        var at = change.changedAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", change.tenantId().toString());
        payload.put("actorId", change.actorId().toString());
        payload.put("paymentType", change.paymentType().name());
        if (change.serviceId() != null) payload.put("serviceId", change.serviceId().toString());
        payload.put("changedAt", at.toInstant().toString());
        int written;
        try {
            written = jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, payment_condition_id, actor_id, occurred_at, payload) "
                    + "SELECT ?, tenant_id, 'PaymentParametersChanged', payment_condition_id, ?, ?, ?::jsonb FROM commercial_reservation.payment_condition "
                    + "WHERE tenant_id = ? AND payment_type = ? AND service_id IS NOT DISTINCT FROM ?::uuid", UUID.randomUUID(), change.actorId(), at,
                    JSON.writeValueAsString(payload), change.tenantId(), change.paymentType().name(), change.serviceId());
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the event payload cannot be written", failure);
        }
        if (written != 1) throw new IllegalStateException("the changed parameter row is not stored, so its event cannot be written");
    }
}
