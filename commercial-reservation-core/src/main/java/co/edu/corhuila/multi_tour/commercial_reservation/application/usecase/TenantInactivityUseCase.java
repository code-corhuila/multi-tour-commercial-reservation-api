package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDeadlineStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantInactivityEffects;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.time.Instant;
import java.util.UUID;

// HU-COM-020 RN-02, RN-03, RN-06, RN-14; AC-20.2 to AC-20.4, AC-20.16, AC-20.18: the inactivation opens the suspension (once) and freezes every open deadline of the tenant at the instant of the event
// with its remaining time and the cause TENANT_INACTIVE; the reactivation closes the suspension (once) and resumes only the deadlines the inactivation froze. A reactivation with nothing open does
// nothing. It reads and writes the suspension and the deadline rows of one tenant, never a security timer.
public class TenantInactivityUseCase implements TenantInactivityEffects {

    private final SuspensionStore suspensions;
    private final TenantDeadlineStore deadlines;
    private final IdGenerator ids;

    public TenantInactivityUseCase(SuspensionStore suspensions, TenantDeadlineStore deadlines, IdGenerator ids) {
        this.suspensions = suspensions;
        this.deadlines = deadlines;
        this.ids = ids;
    }

    @Override
    public void inactivated(UUID tenantId, UUID eventId, Instant at) {
        if (!suspensions.open(ids.newId(), tenantId, at, eventId)) return;
        for (TenantDeadline row : deadlines.openOf(tenantId)) {
            var frozen = row.deadline().frozen(at, row.departureStart(), FreezeCause.TENANT_INACTIVE);
            if (!frozen.equals(row.deadline())) deadlines.save(row.withDeadline(frozen));
        }
    }

    @Override
    public void reactivated(UUID tenantId, UUID eventId, Instant at) {
        if (!suspensions.closeOpen(tenantId, at, eventId)) return;
        for (TenantDeadline row : deadlines.openOf(tenantId)) {
            var resumed = row.deadline().resumed(at, row.departureStart(), FreezeCause.TENANT_INACTIVE, row.departureCancelled());
            if (!resumed.equals(row.deadline())) deadlines.save(row.withDeadline(resumed));
        }
    }
}
