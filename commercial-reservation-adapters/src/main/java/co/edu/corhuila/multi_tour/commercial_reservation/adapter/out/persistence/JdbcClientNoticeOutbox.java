package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The notice of RF-019 as a ClientNotified row of the outbox, written inside the transaction of its fact (HU-COM-015 plan D-03, D-04, D-07). The payload is
// the closed list of the database: the customer reference (read from the reservation here, never from a person's record), the code, the type and the id of
// the triggering event. The unique key of the database makes a second notice of the same fact a no-op, which is the idempotency of RN-06.
public final class JdbcClientNoticeOutbox implements ClientNoticeOutbox {
    private final JdbcTemplate jdbc;

    public JdbcClientNoticeOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID tenantId, UUID reservationId, NoticeType type, String triggeringEventType, UUID triggeringEventId, Instant occurredAt) {
        jdbc.update("INSERT INTO commercial_reservation.outbox_event (tenant_id, event_type, reservation_id, actor_id, occurred_at, payload) "
                + "SELECT r.tenant_id, 'ClientNotified', r.reservation_id, COALESCE((SELECT COALESCE(decided_by, registered_by) FROM "
                + "commercial_reservation.payment_attempt WHERE reservation_id = r.reservation_id ORDER BY COALESCE(decided_at, registered_at) DESC LIMIT 1), "
                + "r.created_by), ?, jsonb_build_object('customerId', r.customer_id::text, 'notificationType', ?::text, 'triggeringEventType', ?::text, "
                + "'triggeringEventId', ?::text) FROM commercial_reservation.reservation r WHERE r.tenant_id = ? AND r.reservation_id = ? "
                + "ON CONFLICT (tenant_id, (payload ->> 'triggeringEventId')) WHERE event_type = 'ClientNotified' DO NOTHING",
                occurredAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC), type.name(), triggeringEventType, triggeringEventId.toString(),
                tenantId, reservationId);
    }
}
