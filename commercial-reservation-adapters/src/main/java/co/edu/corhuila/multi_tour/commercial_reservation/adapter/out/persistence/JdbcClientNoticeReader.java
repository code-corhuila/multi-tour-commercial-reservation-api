package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The notice of a tenant by its event id, with the variables of its fact (HU-COM-015 plan D-05): the tour, the departure, the amount and the balance
// that the triggering event recorded, and the cancellation cause. The tenant is in every join, so another tenant's rows never match. The customer
// reference comes from the payload; the address and the name are read apart.
public final class JdbcClientNoticeReader implements ClientNoticeReader {
    private final JdbcTemplate jdbc;

    public JdbcClientNoticeReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredNotice> find(UUID tenantId, UUID eventId) {
        return jdbc.query("SELECT n.outbox_event_id, n.tenant_id, n.reservation_id, (n.payload ->> 'customerId')::uuid AS customer_id, "
                + "n.payload ->> 'notificationType' AS notification_type, s.commercial_name, sc.hora_inicio, "
                + "(t.payload ->> 'amountCents')::bigint AS amount_cents, COALESCE((t.payload ->> 'pendingBalanceCents')::bigint, r.pending_balance_cents) "
                + "AS pending_balance_cents, r.cancellation_cause FROM commercial_reservation.outbox_event n "
                + "JOIN commercial_reservation.reservation r ON r.tenant_id = n.tenant_id AND r.reservation_id = n.reservation_id "
                + "LEFT JOIN commercial_reservation.catalog_service s ON s.tenant_id = r.tenant_id AND s.service_id = r.service_id "
                + "LEFT JOIN commercial_reservation.salida_capacity sc ON sc.tenant_id = r.tenant_id AND sc.salida_id = r.salida_id "
                + "LEFT JOIN commercial_reservation.outbox_event t ON t.tenant_id = n.tenant_id "
                + "AND t.outbox_event_id = (n.payload ->> 'triggeringEventId')::uuid "
                + "WHERE n.tenant_id = ? AND n.outbox_event_id = ? AND n.event_type = 'ClientNotified'", (row, index) -> new StoredNotice(
                        row.getObject("outbox_event_id", UUID.class), row.getObject("tenant_id", UUID.class), row.getObject("reservation_id", UUID.class),
                        row.getObject("customer_id", UUID.class), NoticeType.valueOf(row.getString("notification_type")),
                        row.getString("commercial_name"), instant(row.getObject("hora_inicio", OffsetDateTime.class)),
                        (Long) row.getObject("amount_cents"), (Long) row.getObject("pending_balance_cents"), row.getString("cancellation_cause")),
                tenantId, eventId).stream().findFirst();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
