package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

// A reason is disabled, never deleted: the only update allowed is the status of a row of the same tenant.
public final class JdbcAuthorizableReasonRepository implements AuthorizableReasonRepository {
    private static final String COLUMNS = "SELECT authorizable_reason_id, tenant_id, reason, status "
            + "FROM commercial_reservation.authorizable_reason ";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcAuthorizableReasonRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void save(AuthorizableReason reason) {
        OffsetDateTime now = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        String status = reason.enabled() ? "ENABLED" : "DISABLED";
        try {
            int changed = jdbc.update("UPDATE commercial_reservation.authorizable_reason SET status = ?, "
                    + "updated_at = ? WHERE tenant_id = ? AND authorizable_reason_id = ?",
                    status, now, reason.tenantId(), reason.reasonId());
            if (changed == 1) {
                return;
            }
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM "
                    + "commercial_reservation.authorizable_reason WHERE authorizable_reason_id = ?)",
                    Boolean.class, reason.reasonId()))) {
                throw new IllegalStateException("reason belongs to another tenant");
            }
            jdbc.update("INSERT INTO commercial_reservation.authorizable_reason "
                    + "(authorizable_reason_id, tenant_id, reason, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", reason.reasonId(), reason.tenantId(), reason.text(), status, now, now);
        } catch (DuplicateKeyException duplicate) {
            throw new DomainException("reason", "the reason already exists");
        }
    }

    @Override
    public Optional<AuthorizableReason> findByTenantAndId(UUID tenantId, UUID reasonId) {
        return jdbc.query(COLUMNS + "WHERE tenant_id = ? AND authorizable_reason_id = ?",
                (row, index) -> map(row), tenantId, reasonId).stream().findFirst();
    }

    @Override
    public List<AuthorizableReason> findByTenant(UUID tenantId) {
        return jdbc.query(COLUMNS + "WHERE tenant_id = ? ORDER BY created_at, authorizable_reason_id",
                (row, index) -> map(row), tenantId);
    }

    private static AuthorizableReason map(ResultSet row) throws SQLException {
        return new AuthorizableReason(row.getObject("tenant_id", UUID.class),
                row.getObject("authorizable_reason_id", UUID.class), row.getString("reason"),
                "ENABLED".equals(row.getString("status")));
    }
}
