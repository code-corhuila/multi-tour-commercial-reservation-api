package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantEventOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyTenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantInactivityEffects;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatusCopy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// One use case for the four tenant events (plan D-01). The id is recorded in the same transaction as the effect, also
// when the event is not newer, so the relay stops (DP-TSC-06). An event that cannot create the copy records nothing.
public final class ApplyTenantEventUseCase implements ApplyTenantEvent {

    private final TenantStatusStore store;
    private final ProcessedEvents processed;
    private final TenantEventTransaction transaction;
    private final TenantInactivityEffects effects;

    public ApplyTenantEventUseCase(TenantStatusStore store, ProcessedEvents processed, TenantEventTransaction transaction) {
        this(store, processed, transaction, new TenantInactivityEffects() {
            @Override public void inactivated(UUID tenantId, UUID eventId, Instant at) { }
            @Override public void reactivated(UUID tenantId, UUID eventId, Instant at) { }
        });
    }

    // HU-COM-020 RN-06: the effects of the inactivation and the reactivation run in this transaction, only for an event that moved the status.
    public ApplyTenantEventUseCase(TenantStatusStore store, ProcessedEvents processed, TenantEventTransaction transaction, TenantInactivityEffects effects) {
        this.store = store;
        this.processed = processed;
        this.transaction = transaction;
        this.effects = effects;
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
            Optional<TenantStatusCopy> next = kept.flatMap(copy -> copy.apply(event));
            boolean moved = next.map(store::moveForward).orElse(false);
            if (moved && kept.get().status() != next.get().status()) {
                if (next.get().status() == TenantStatus.INACTIVE) effects.inactivated(event.tenantId(), event.eventId(), event.occurredAt());
                else effects.reactivated(event.tenantId(), event.eventId(), event.occurredAt());
            }
            processed.record(event.eventId());
            return moved ? TenantEventOutcome.APPLIED : TenantEventOutcome.NOT_APPLIED;
        });
    }
}
