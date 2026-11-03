package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyTenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatusCopy;
import java.util.Optional;

// One use case for the four tenant events (plan D-01). The id is recorded in the same transaction as the effect, also
// when the event is not newer, so the relay stops (DP-TSC-06). An event that cannot create the copy records nothing.
public final class ApplyTenantEventUseCase implements ApplyTenantEvent {

    private final TenantStatusStore store;
    private final ProcessedEvents processed;
    private final TenantEventTransaction transaction;

    public ApplyTenantEventUseCase(TenantStatusStore store, ProcessedEvents processed, TenantEventTransaction transaction) {
        this.store = store;
        this.processed = processed;
        this.transaction = transaction;
    }

    @Override
    public TenantEventOutcome apply(TenantEvent event) {
        return transaction.inTransaction(() -> {
            Optional<TenantStatusCopy> kept = store.findForUpdate(event.tenantId());
            if (processed.wasProcessed(event.eventId())) {
                return TenantEventOutcome.NOT_APPLIED;
            }
            if (kept.isEmpty()) {
                if (store.insertIfAbsent(TenantStatusCopy.from(event))) {
                    processed.record(event.eventId());
                    return TenantEventOutcome.APPLIED;
                }
                kept = store.findForUpdate(event.tenantId());
            }
            boolean moved = kept.flatMap(copy -> copy.apply(event)).map(store::moveForward).orElse(false);
            processed.record(event.eventId());
            return moved ? TenantEventOutcome.APPLIED : TenantEventOutcome.NOT_APPLIED;
        });
    }
}
