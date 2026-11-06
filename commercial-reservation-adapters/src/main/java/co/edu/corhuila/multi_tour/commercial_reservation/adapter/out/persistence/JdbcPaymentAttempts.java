package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The attempts of a reservation in payment_attempt and the open seat in capacity_allocation. An attempt is written once and decided
// once: the upsert repeats the same values for a stored attempt and the database trigger refuses any other change to a decided one.
final class JdbcPaymentAttempts {
    private final JdbcTemplate jdbc;

    JdbcPaymentAttempts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void write(Reservation reservation) {
        for (PaymentAttempt a : reservation.attempts()) {
            jdbc.update("INSERT INTO commercial_reservation.payment_attempt (payment_attempt_id, tenant_id, reservation_id, "
                    + "payment_method, received_amount_cents, validation_status, payment_status_snapshot, registered_by, registered_at, "
                    + "decided_by, decided_at, decision_reason, verified_amount_cents, applied_amount_cents, excess_amount_cents, "
                    + "treatment_amount_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (payment_attempt_id) DO UPDATE SET validation_status = EXCLUDED.validation_status, "
                    + "decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at, decision_reason = EXCLUDED.decision_reason, "
                    + "verified_amount_cents = EXCLUDED.verified_amount_cents, applied_amount_cents = EXCLUDED.applied_amount_cents, "
                    + "excess_amount_cents = EXCLUDED.excess_amount_cents, treatment_amount_cents = EXCLUDED.treatment_amount_cents "
                    + "WHERE payment_attempt.tenant_id = EXCLUDED.tenant_id",
                    a.attemptId(), reservation.tenantId(), reservation.reservationId(), a.method().name(), a.amount().amount(),
                    a.validationStatus() == null ? null : a.validationStatus().name(), a.paymentStatusSnapshot().name(), a.registeredBy(),
                    time(a.registeredAt()), a.decidedBy(), a.decidedAt() == null ? null : time(a.decidedAt()), a.decisionReason(),
                    amount(a.verifiedAmount()), amount(a.appliedAmount()), amount(a.excessAmount()), amount(a.treatmentAmount()));
        }
    }

    // The seat is held by the capacity row (SalidaCapacityHolder); this keeps the detail of it, one open row per reservation.
    void keepSeat(Reservation reservation) {
        if (!reservation.seatHeld() || openSeat(reservation.tenantId(), reservation.reservationId())) return;
        jdbc.update("INSERT INTO commercial_reservation.capacity_allocation (capacity_allocation_id, tenant_id, salida_id, reservation_id, "
                + "party_size, allocation_kind, allocated_at) VALUES (?, ?, ?, ?, ?, 'RESERVATION', ?)", UUID.randomUUID(),
                reservation.tenantId(), reservation.departureId(), reservation.reservationId(), reservation.people(),
                time(Instant.now()));
    }

    boolean openSeat(UUID tenantId, UUID reservationId) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? "
                + "AND reservation_id = ? AND released_at IS NULL", Integer.class, tenantId, reservationId) > 0;
    }

    // The batch of openSeat: the reservations, among the ones named, that hold an open seat. One statement.
    Set<UUID> openSeats(UUID tenantId, String reservationIds) {
        return new HashSet<>(jdbc.query("SELECT DISTINCT reservation_id FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? "
                + "AND reservation_id = ANY (?::uuid[]) AND released_at IS NULL", (row, index) -> row.getObject(1, UUID.class), tenantId,
                reservationIds));
    }

    // The batch of read: the attempts of the reservations named, each list in the order of the single read. One statement.
    Map<UUID, List<PaymentAttempt>> readAll(UUID tenantId, String reservationIds) {
        Map<UUID, List<PaymentAttempt>> byReservation = new HashMap<>();
        jdbc.query("SELECT * FROM commercial_reservation.payment_attempt WHERE tenant_id = ? AND reservation_id = ANY (?::uuid[]) "
                + "ORDER BY registered_at, payment_attempt_id", (row, index) -> byReservation.computeIfAbsent(
                        row.getObject("reservation_id", UUID.class), id -> new java.util.ArrayList<>()).add(attempt(row)), tenantId, reservationIds);
        return byReservation;
    }

    List<PaymentAttempt> read(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT * FROM commercial_reservation.payment_attempt WHERE tenant_id = ? AND reservation_id = ? "
                + "ORDER BY registered_at, payment_attempt_id", (row, index) -> attempt(row), tenantId, reservationId);
    }

    private static PaymentAttempt attempt(ResultSet row) throws SQLException {
        String status = row.getString("validation_status");
        OffsetDateTime decidedAt = row.getObject("decided_at", OffsetDateTime.class);
        return new PaymentAttempt(row.getObject("payment_attempt_id", UUID.class), PaymentMethod.valueOf(row.getString("payment_method")),
                new Cents(row.getLong("received_amount_cents")), status == null ? null : ValidationStatus.valueOf(status),
                PaymentStatus.valueOf(row.getString("payment_status_snapshot")), row.getObject("registered_by", UUID.class),
                row.getObject("registered_at", OffsetDateTime.class).toInstant(), row.getObject("decided_by", UUID.class),
                decidedAt == null ? null : decidedAt.toInstant(), row.getString("decision_reason"), cents(row, "verified_amount_cents"),
                cents(row, "applied_amount_cents"), cents(row, "excess_amount_cents"), cents(row, "treatment_amount_cents"));
    }

    private static Cents cents(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : new Cents(value);
    }

    private static Long amount(Cents value) {
        return value == null ? null : value.amount();
    }

    private static OffsetDateTime time(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
