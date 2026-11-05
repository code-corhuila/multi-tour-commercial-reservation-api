package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OutboxDeliveryStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

// The delivery data of the outbox for job 7 (HU-COM-015 plan D-08): the events of every type of the domain across tenants, with the envelope of ADR-004 D4.
// The aggregate is the one reference the event carries; an event with no payload column (the catalog ones) shows its two statuses. Every write is one UPDATE
// of the delivery columns guarded by the pending state, so the engine guard of the database and this class agree on what may change.
public final class JdbcOutboxDeliveryStore implements OutboxDeliveryStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EVENT_VERSION = 1;
    private static final String COLUMNS = "outbox_event_id, event_type, tenant_id, COALESCE(service_id, association_id, discount_rule_id, reservation_id, "
            + "customer_id) AS aggregate_id, COALESCE(payload, jsonb_strip_nulls(jsonb_build_object('previousCatalogStatus', previous_catalog_status, "
            + "'newCatalogStatus', new_catalog_status)))::text AS payload, COALESCE(correlation_id, outbox_event_id::text) AS correlation_id, occurred_at, "
            + "published_at, failed_at, attempts, last_error, next_attempt_at";
    private static final String PENDING = " FROM commercial_reservation.outbox_event WHERE published_at IS NULL AND failed_at IS NULL "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= ?)";
    private final JdbcTemplate jdbc;

    public JdbcOutboxDeliveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxEventPage pending(Instant now, int page, int limit) {
        OffsetDateTime at = at(now);
        Long total = jdbc.queryForObject("SELECT count(*)" + PENDING, Long.class, at);
        var items = jdbc.query("SELECT " + COLUMNS + PENDING + " ORDER BY next_attempt_at NULLS FIRST, occurred_at LIMIT ? OFFSET ?", rows(), at, limit,
                (long) (page - 1) * limit);
        return new OutboxEventPage(items, total == null ? 0 : total);
    }

    @Override
    public Optional<OutboxEventView> find(UUID eventId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM commercial_reservation.outbox_event WHERE outbox_event_id = ?", rows(), eventId).stream().findFirst();
    }

    @Override
    public boolean markPublished(UUID eventId, Instant now) {
        return jdbc.update("UPDATE commercial_reservation.outbox_event SET published_at = ?, next_attempt_at = NULL WHERE outbox_event_id = ? "
                + "AND published_at IS NULL AND failed_at IS NULL", at(now), eventId) == 1;
    }

    @Override
    public boolean recordAttempt(UUID eventId, int expectedAttempts, String lastError, Instant nextAttemptAt, Instant failedAt) {
        return jdbc.update("UPDATE commercial_reservation.outbox_event SET attempts = attempts + 1, last_error = ?, next_attempt_at = ?, failed_at = ? "
                + "WHERE outbox_event_id = ? AND attempts = ? AND published_at IS NULL AND failed_at IS NULL", lastError,
                nextAttemptAt == null ? null : at(nextAttemptAt), failedAt == null ? null : at(failedAt), eventId, expectedAttempts) == 1;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static RowMapper<OutboxEventView> rows() {
        return (row, index) -> {
            Instant published = instant(row.getObject("published_at", OffsetDateTime.class));
            boolean failed = row.getObject("failed_at") != null;
            return new OutboxEventView(row.getObject("outbox_event_id", UUID.class), row.getString("event_type"), EVENT_VERSION,
                    row.getObject("tenant_id", UUID.class), row.getObject("aggregate_id", UUID.class), payload(row.getString("payload")),
                    row.getString("correlation_id"), instant(row.getObject("occurred_at", OffsetDateTime.class)), published,
                    failed ? OutboxStatus.FAILED : published != null ? OutboxStatus.PUBLISHED : OutboxStatus.PENDING, row.getInt("attempts"),
                    row.getString("last_error"), instant(row.getObject("next_attempt_at", OffsetDateTime.class)));
        };
    }

    private static Map<String, Object> payload(String json) {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("the payload of an outbox event is not a JSON object", failure);
        }
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
