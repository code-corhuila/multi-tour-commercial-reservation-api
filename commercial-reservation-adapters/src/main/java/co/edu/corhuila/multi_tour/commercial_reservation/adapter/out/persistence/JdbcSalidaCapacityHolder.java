package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The hold is one conditional update of the capacity row, held plus requested within the effective capacity (ADR-004 D4): never a
// read followed by a write, so the loser of a race gets false. The detail of the seat (capacity_allocation) is written with the
// reservation; the release closes that open row and gives the seats back in the same transaction of the caller.
public final class JdbcSalidaCapacityHolder implements SalidaCapacityHolder {
    private final JdbcTemplate jdbc;

    public JdbcSalidaCapacityHolder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hold(UUID tenantId, UUID salidaId, int requested) {
        return jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = held_seats + ? WHERE tenant_id = ? "
                + "AND salida_id = ? AND held_seats + ? <= effective_capacity", requested, tenantId, salidaId, requested) == 1;
    }

    @Override
    public int release(UUID tenantId, UUID reservationId) {
        List<int[]> open = jdbc.query("SELECT salida_id, party_size FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? "
                + "AND reservation_id = ? AND released_at IS NULL FOR UPDATE", (row, index) -> new int[] {row.getInt("party_size")},
                tenantId, reservationId);
        if (open.isEmpty()) return 0;
        UUID salida = jdbc.queryForObject("SELECT salida_id FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? "
                + "AND reservation_id = ? AND released_at IS NULL", UUID.class, tenantId, reservationId);
        int seats = open.get(0)[0];
        jdbc.update("UPDATE commercial_reservation.capacity_allocation SET released_at = ? WHERE tenant_id = ? AND reservation_id = ? "
                + "AND released_at IS NULL", OffsetDateTime.now(ZoneOffset.UTC), tenantId, reservationId);
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = GREATEST(held_seats - ?, 0) WHERE tenant_id = ? "
                + "AND salida_id = ?", seats, tenantId, salida);
        return seats;
    }

    // HU-COM-012 D-05: fewer travellers give back only their seats, from the departure of the open allocation. The allocation row itself is
    // kept in step with the reservation by the repository, in the same transaction.
    @Override
    public int releaseSeats(UUID tenantId, UUID reservationId, int seats) {
        List<UUID> open = jdbc.queryForList("SELECT salida_id FROM commercial_reservation.capacity_allocation WHERE tenant_id = ? "
                + "AND reservation_id = ? AND released_at IS NULL FOR UPDATE", UUID.class, tenantId, reservationId);
        if (open.isEmpty()) return 0;
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = GREATEST(held_seats - ?, 0) WHERE tenant_id = ? "
                + "AND salida_id = ?", seats, tenantId, open.get(0));
        return seats;
    }

    // HU-COM-012 D-05: the new departure is held by the conditional update first and the old one is released only when that succeeded, both
    // in the transaction of the caller, so a failure leaves the seats where they were.
    @Override
    public boolean move(UUID tenantId, UUID reservationId, UUID fromSalidaId, UUID toSalidaId, int seats) {
        if (!hold(tenantId, toSalidaId, seats)) return false;
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = GREATEST(held_seats - ?, 0) WHERE tenant_id = ? "
                + "AND salida_id = ?", seats, tenantId, fromSalidaId);
        return true;
    }
}
