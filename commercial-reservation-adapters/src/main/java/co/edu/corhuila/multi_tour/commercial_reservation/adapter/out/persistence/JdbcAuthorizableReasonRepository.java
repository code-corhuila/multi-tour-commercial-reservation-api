package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReasonRuleViolation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

// A reason is disabled, never deleted: the only update allowed is the status of a row of the same tenant.
public final class JdbcAuthorizableReasonRepository implements AuthorizableReasonRepository, AuthorizableReasonTimes {
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
            throw new ReasonRuleViolation("the reason already exists");
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

    @Override
    public Map<UUID, Times> timesByTenant(UUID tenantId) {
        Map<UUID, Times> found = new HashMap<>();
        jdbc.query("SELECT authorizable_reason_id, created_at, updated_at FROM commercial_reservation.authorizable_reason "
                + "WHERE tenant_id = ?", row -> {
            found.put(row.getObject("authorizable_reason_id", UUID.class), new Times(
                    row.getObject("created_at", OffsetDateTime.class).toInstant(),
                    row.getObject("updated_at", OffsetDateTime.class).toInstant()));
        }, tenantId);
        return found;
    }

    @Override
    public void lockCatalogue(UUID tenantId) {
        jdbc.queryForList("SELECT authorizable_reason_id FROM commercial_reservation.authorizable_reason "
                + "WHERE tenant_id = ? ORDER BY authorizable_reason_id FOR UPDATE", UUID.class, tenantId);
    }

    private static AuthorizableReason map(ResultSet row) throws SQLException {
        return new AuthorizableReason(row.getObject("tenant_id", UUID.class),
                row.getObject("authorizable_reason_id", UUID.class), row.getString("reason"),
                "ENABLED".equals(row.getString("status")));
    }
}
