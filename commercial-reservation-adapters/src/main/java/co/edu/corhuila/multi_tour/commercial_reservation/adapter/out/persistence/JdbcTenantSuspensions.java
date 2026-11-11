package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-020 RN-14, plan D-02 of the db (tenant_suspension, its guard and its unique keys): the append-only history of the periods of inactivity of a tenant. A period is opened once (the partial unique
// key of the open one and the unique opening event refuse a second) and closed once by the guard; both writes run inside the transaction of the tenant event. Every query leads with the tenant.
public final class JdbcTenantSuspensions implements SuspensionStore {
    private final JdbcTemplate jdbc;

    public JdbcTenantSuspensions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean open(UUID id, UUID tenantId, Instant at, UUID openingEventId) {
        return jdbc.update("INSERT INTO commercial_reservation.tenant_suspension (tenant_suspension_id, tenant_id, suspended_at, suspended_by_event_id) "
                + "SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM commercial_reservation.tenant_suspension WHERE tenant_id = ? AND resumed_at IS NULL) ON CONFLICT DO NOTHING",
                id, tenantId, time(at), openingEventId, tenantId) == 1;
    }

    @Override
    public boolean closeOpen(UUID tenantId, Instant at, UUID closingEventId) {
        return jdbc.update("UPDATE commercial_reservation.tenant_suspension SET resumed_at = GREATEST(?, suspended_at), resumed_by_event_id = ? WHERE tenant_id = ? AND resumed_at IS NULL",
                time(at), closingEventId, tenantId) == 1;
    }

    @Override
    public SuspensionIntervals intervals(UUID tenantId) {
        return new SuspensionIntervals(jdbc.query("SELECT suspended_at, resumed_at FROM commercial_reservation.tenant_suspension WHERE tenant_id = ? ORDER BY suspended_at",
                (row, index) -> new SuspensionIntervals.Interval(instant(row, "suspended_at"), instant(row, "resumed_at")), tenantId));
    }
}
