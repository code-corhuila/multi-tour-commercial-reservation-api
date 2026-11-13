package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-013 DP-02a: cancellation_penalty_policy, one row per service and, optionally, one for the whole tenant; a change replaces the row, keeping only the last author,
// reason and time. It is also the CancellationPolicyReader: the service row wins over the tenant row, and with neither the answer is empty (no penalty). Nothing is deleted.
public final class JdbcCancellationPolicies implements CancellationPolicyRepository, CancellationPolicyReader {
    private final JdbcTemplate jdbc;

    public JdbcCancellationPolicies(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CancellationPolicy> findByTenant(UUID tenantId) {
        return jdbc.query("SELECT service_id, penalty_bp, changed_by, change_reason, changed_at FROM commercial_reservation.cancellation_penalty_policy "
                + "WHERE tenant_id = ? ORDER BY service_id NULLS FIRST", (row, index) -> new CancellationPolicy(row.getObject("service_id", UUID.class),
                row.getInt("penalty_bp"), row.getObject("changed_by", UUID.class), row.getString("change_reason"),
                row.getObject("changed_at", OffsetDateTime.class).toInstant()), tenantId);
    }

    @Override
    public boolean serviceExists(UUID tenantId, UUID serviceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM commercial_reservation.catalog_service WHERE tenant_id = ? AND service_id = ?)",
                Boolean.class, tenantId, serviceId));
    }

    @Override
    public void save(UUID tenantId, CancellationPolicy policy) {
        OffsetDateTime at = policy.changedAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        int updated = jdbc.update("UPDATE commercial_reservation.cancellation_penalty_policy SET penalty_bp = ?, changed_by = ?, change_reason = ?, changed_at = ? "
                + "WHERE tenant_id = ? AND service_id IS NOT DISTINCT FROM ?::uuid", policy.penaltyBasisPoints(), policy.changedBy(), policy.changeReason(), at,
                tenantId, policy.serviceId());
        if (updated > 0) return;
        jdbc.update("INSERT INTO commercial_reservation.cancellation_penalty_policy (cancellation_penalty_policy_id, tenant_id, service_id, penalty_bp, changed_by, "
                + "change_reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, policy.serviceId(), policy.penaltyBasisPoints(),
                policy.changedBy(), policy.changeReason(), at);
    }

    // The service row, else the tenant row, else nothing. The moment and the departure are the port's contract; this adapter has no scale by time (DD-8).
    @Override
    public OptionalInt penaltyBasisPoints(UUID tenantId, UUID serviceId, Instant departureStart, Instant now) {
        List<Integer> rows = jdbc.queryForList("SELECT penalty_bp FROM commercial_reservation.cancellation_penalty_policy WHERE tenant_id = ? "
                + "AND (service_id = ? OR service_id IS NULL) ORDER BY service_id NULLS LAST LIMIT 1", Integer.class, tenantId, serviceId);
        return rows.isEmpty() ? OptionalInt.empty() : OptionalInt.of(rows.get(0));
    }
}
