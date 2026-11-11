package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantIdPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ActiveTenantQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The active tenants of the copy (HU-WRK-001 DP-08): ids only, ordered by the primary key so a page is stable.
public final class JdbcActiveTenantQueries implements ActiveTenantQueries {
    private final JdbcTemplate jdbc;

    public JdbcActiveTenantQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TenantIdPage page(int page, int limit) {
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.tenant_status WHERE status = 'ACTIVE'", Long.class);
        List<UUID> ids = jdbc.query("SELECT tenant_id FROM commercial_reservation.tenant_status WHERE status = 'ACTIVE' ORDER BY tenant_id LIMIT ? OFFSET ?",
                (row, index) -> row.getObject("tenant_id", UUID.class), limit, (long) (page - 1) * limit);
        return new TenantIdPage(ids, total);
    }
}
