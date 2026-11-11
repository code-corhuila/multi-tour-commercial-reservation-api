package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationSettlement;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-013 plan D-02 of the db: one row per reservation cancelled at the customer's request, written in the transaction of the
// cancellation after the reservation was updated to CANCELLED (the composite key checks tenant, reservation and cause). Append and read
// only: the application user holds no UPDATE and no DELETE (AC-D13.5). The reason is stored and read, never logged. Every query leads
// with the tenant. refundDecisionOpened is not a column: it is true exactly when the refundable amount is above zero.
public final class JdbcCancellationRecords implements CancellationRecords {
    private final JdbcTemplate jdbc;

    public JdbcCancellationRecords(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID tenantId, CancellationRecord record) {
        CancellationSettlement s = record.settlement();
        jdbc.update("INSERT INTO commercial_reservation.reservation_cancellation (reservation_cancellation_id, tenant_id, reservation_id, "
                + "cancellation_cause, reason, validated_amount_cents, penalty_bp, penalty_amount_cents, refundable_amount_cents, registered_by, "
                + "registered_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, record.reservationId(),
                record.origin().name(), record.reason(), s.validated().amount(), s.penaltyBasisPoints(), s.penalty().amount(),
                s.refundable().amount(), record.cancelledBy(), OffsetDateTime.ofInstant(record.cancelledAt(), ZoneOffset.UTC));
    }

    @Override
    public Optional<CancellationRecord> find(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT cancellation_cause, reason, validated_amount_cents, penalty_bp, penalty_amount_cents, refundable_amount_cents, "
                + "registered_by, registered_at FROM commercial_reservation.reservation_cancellation WHERE tenant_id = ? AND reservation_id = ?",
                (row, index) -> new CancellationRecord(reservationId, row.getString("reason"),
                        CancellationCause.valueOf(row.getString("cancellation_cause")),
                        new CancellationSettlement(new Cents(row.getLong("validated_amount_cents")), row.getInt("penalty_bp"),
                                new Cents(row.getLong("penalty_amount_cents")), new Cents(row.getLong("refundable_amount_cents"))),
                        row.getLong("refundable_amount_cents") > 0, row.getObject("registered_by", UUID.class),
                        row.getObject("registered_at", OffsetDateTime.class).toInstant()), tenantId, reservationId).stream().findFirst();
    }
}
