package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The hold is one conditional update of the capacity row, held plus requested within the effective capacity (ADR-004 D4): never a
// read followed by a write, so the loser of a race gets false. The detail of the seat (capacity_allocation) is written with the
// reservation; the same statement re-checks that the departure is still SCHEDULED, so a concurrent cancellation keeps no seat on it
// (the start instant is judged by the application with its clock); the release closes that open row and gives the seats back in the same transaction of the caller.
public final class JdbcSalidaCapacityHolder implements SalidaCapacityHolder {
    private final JdbcTemplate jdbc;

    public JdbcSalidaCapacityHolder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hold(UUID tenantId, UUID salidaId, int requested) {
        return jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = held_seats + ? WHERE tenant_id = ? "
                + "AND salida_id = ? AND status = 'SCHEDULED' AND held_seats + ? <= effective_capacity", requested, tenantId, salidaId, requested) == 1;
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
}
