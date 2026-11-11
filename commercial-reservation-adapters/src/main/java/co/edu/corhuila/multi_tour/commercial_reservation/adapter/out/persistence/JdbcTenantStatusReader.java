package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The status of a tenant for the authentication filter: one read by primary key. A failure is thrown (plan D-05).
public final class JdbcTenantStatusReader implements TenantStatusReader {
    private final JdbcTemplate jdbc;

    public JdbcTenantStatusReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantStatus> statusOf(UUID tenantId) {
        return jdbc.query("SELECT status FROM commercial_reservation.tenant_status WHERE tenant_id = ?",
                (row, index) -> TenantStatus.valueOf(row.getString("status")), tenantId).stream().findFirst();
    }
}
