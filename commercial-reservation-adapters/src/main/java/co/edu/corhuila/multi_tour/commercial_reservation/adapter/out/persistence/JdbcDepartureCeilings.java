package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CeilingState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureCeilings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The ceiling is salida_capacity.effective_capacity, read and written by tenant and departure. The write names the tenant too, so a row of another tenant is
// never touched, and it keeps the held seats within the capacity (chk_salida_capacity_held_within_effective) as a second guard.
public final class JdbcDepartureCeilings implements DepartureCeilings {
    private static final String SELECT = "SELECT effective_capacity, held_seats, status FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?";
    private final JdbcTemplate jdbc;

    public JdbcDepartureCeilings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CeilingState> find(UUID tenantId, UUID salidaId) {
        return read(SELECT, tenantId, salidaId);
    }

    @Override
    public Optional<CeilingState> findForUpdate(UUID tenantId, UUID salidaId) {
        return read(SELECT + " FOR UPDATE", tenantId, salidaId);
    }

    @Override
    public void setCeiling(UUID tenantId, UUID salidaId, int ceiling) {
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET effective_capacity = ? WHERE tenant_id = ? AND salida_id = ?", ceiling, tenantId, salidaId);
    }

    private Optional<CeilingState> read(String sql, UUID tenantId, UUID salidaId) {
        return jdbc.query(sql, (row, index) -> new CeilingState(row.getInt("effective_capacity"), row.getInt("held_seats"),
                !"FINALIZED".equals(row.getString("status")) && !"CANCELLED".equals(row.getString("status"))), tenantId, salidaId).stream().findFirst();
    }
}
