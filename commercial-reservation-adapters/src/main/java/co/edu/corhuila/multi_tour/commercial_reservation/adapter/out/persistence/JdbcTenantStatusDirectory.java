package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Public identifier to tenant through the copy (RN-TSC-07): one read by the unique index of the identifier.
public final class JdbcTenantStatusDirectory implements TenantDirectory {
    private final JdbcTemplate jdbc;

    public JdbcTenantStatusDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantRef> resolve(String identifier) {
        return jdbc.query("SELECT tenant_id, status FROM commercial_reservation.tenant_status WHERE identifier = ?",
                (row, index) -> new TenantRef(row.getObject("tenant_id", UUID.class), "ACTIVE".equals(row.getString("status"))),
                identifier).stream().findFirst();
    }
}
