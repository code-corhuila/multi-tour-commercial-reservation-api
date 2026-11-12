package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ModificationOutcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 plan D-02 of the db: one immutable row per change, written in the transaction of the modification. Append and read only: the
// application user holds no UPDATE and no DELETE. The reason is stored and read, never logged. Every query leads with the tenant.
public final class JdbcReservationModifications implements ReservationModifications {
    private final JdbcTemplate jdbc;

    public JdbcReservationModifications(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID tenantId, ReservationModificationRecord r) {
        ModificationOutcome o = r.outcome();
        jdbc.update("INSERT INTO commercial_reservation.reservation_modification (reservation_modification_id, tenant_id, reservation_id, "
                + "change_type, reason, previous_final_value_cents, new_final_value_cents, previous_pending_balance_cents, "
                + "new_pending_balance_cents, excess_amount_cents, previous_party_size, new_party_size, previous_service_id, new_service_id, "
                + "previous_salida_id, new_salida_id, registered_by, registered_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.modificationId(), tenantId, r.reservationId(), r.changeType().name(), r.reason(), o.oldFinalValue().amount(),
                o.newFinalValue().amount(), o.oldPendingBalance().amount(), o.newPendingBalance().amount(), o.excess().amount(),
                r.previousPartySize(), r.newPartySize(), r.serviceId(), r.serviceId(), r.previousSalidaId(), r.newSalidaId(), r.registeredBy(),
                OffsetDateTime.ofInstant(r.registeredAt(), ZoneOffset.UTC));
    }

    @Override
    public ModificationPage page(UUID tenantId, UUID reservationId, int page, int limit) {
        List<ReservationModificationRecord> items = jdbc.query("SELECT * FROM commercial_reservation.reservation_modification WHERE tenant_id = ? "
                + "AND reservation_id = ? ORDER BY registered_at DESC, reservation_modification_id LIMIT ? OFFSET ?", (row, index) ->
                new ReservationModificationRecord(row.getObject("reservation_modification_id", UUID.class), reservationId,
                        ChangeType.valueOf(row.getString("change_type")), row.getString("reason"),
                        new ModificationOutcome(new Cents(row.getLong("previous_final_value_cents")), new Cents(row.getLong("new_final_value_cents")),
                                new Cents(row.getLong("previous_pending_balance_cents")), new Cents(row.getLong("new_pending_balance_cents")),
                                new Cents(row.getLong("excess_amount_cents")), row.getInt("new_party_size") - row.getInt("previous_party_size")),
                        row.getInt("previous_party_size"), row.getInt("new_party_size"), row.getObject("new_service_id", UUID.class),
                        row.getObject("previous_salida_id", UUID.class), row.getObject("new_salida_id", UUID.class),
                        row.getObject("registered_by", UUID.class), row.getObject("registered_at", OffsetDateTime.class).toInstant()),
                tenantId, reservationId, limit, (long) (page - 1) * limit);
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation_modification WHERE tenant_id = ? "
                + "AND reservation_id = ?", Long.class, tenantId, reservationId);
        return new ModificationPage(items, total);
    }
}
