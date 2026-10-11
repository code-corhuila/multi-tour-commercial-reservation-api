package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class JdbcSalidaCapacityReader implements SalidaCapacityReader {
    private final JdbcTemplate jdbc;

    public JdbcSalidaCapacityReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int maxHeldSeats(UUID tenantId, UUID serviceId) {
        // Autocommit would release the locks before PATCH saves the new capacity.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Capacity checks require the catalog update transaction");
        }
        // PostgreSQL cannot lock an aggregate result; lock departures first, then compute the maximum.
        return jdbc.query("SELECT held_seats FROM commercial_reservation.salida_capacity "
                        + "WHERE tenant_id = ? AND service_id = ? ORDER BY salida_capacity_id FOR SHARE",
                (row, index) -> row.getInt("held_seats"), tenantId, serviceId)
                .stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
