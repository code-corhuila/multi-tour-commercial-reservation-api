package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-016 plan D-09, DP-10; -db 028: RefundAuthorized in the transaction of the authorization. The payload is closed to six keys of identifiers, an
// amount and two codes; authorizedBy and the actor are null when Multitour determined the refund. The reason is never copied here (AC-16.18).
public final class JdbcRefundAuthorizedOutbox implements RefundAuthorizedOutbox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public JdbcRefundAuthorizedOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(RefundAuthorization refund) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundAuthorizationId", refund.id().toString());
        payload.put("reservationId", refund.reservationId().toString());
        payload.put("amountCents", refund.authorizedAmount().amount());
        payload.put("origin", refund.origin().name());
        payload.put("authorizationMode", refund.mode().name());
        payload.put("authorizedBy", refund.authorizedBy() == null ? null : refund.authorizedBy().toString());
        try {
            jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, reservation_id, actor_id, occurred_at, payload) "
                    + "VALUES (?, 'RefundAuthorized', ?, ?, ?, ?::jsonb)", refund.tenantId(), refund.reservationId(), refund.authorizedBy(),
                    time(refund.authorizedAt()), JSON.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the RefundAuthorized payload cannot be written", e);
        }
    }
}
