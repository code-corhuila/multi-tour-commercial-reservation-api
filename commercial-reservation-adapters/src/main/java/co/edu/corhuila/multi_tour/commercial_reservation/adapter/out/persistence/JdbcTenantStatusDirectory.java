package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Public identifier to tenant through the copy (RN-TSC-07): one read by the unique index of the identifier. The creation check
// of HU-COM-007 (RN-16) asks with the tenant id as text (plan D-03, DD-2), so an identifier that is not a known slug and is a
// UUID is read by the primary key; a slug is never reinterpreted and an unknown value stays empty.
public final class JdbcTenantStatusDirectory implements TenantDirectory {
    private static final String SELECT = "SELECT tenant_id, status FROM commercial_reservation.tenant_status WHERE ";
    private final JdbcTemplate jdbc;

    public JdbcTenantStatusDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantRef> resolve(String identifier) {
        Optional<TenantRef> bySlug = query("identifier = ?", identifier);
        if (bySlug.isPresent()) return bySlug;
        try {
            return query("tenant_id = ?", UUID.fromString(identifier));
        } catch (IllegalArgumentException | NullPointerException notAnId) {
            return Optional.empty();
        }
    }

    private Optional<TenantRef> query(String predicate, Object value) {
        return jdbc.query(SELECT + predicate,
                (row, index) -> new TenantRef(row.getObject("tenant_id", UUID.class), "ACTIVE".equals(row.getString("status"))),
                value).stream().findFirst();
    }
}
