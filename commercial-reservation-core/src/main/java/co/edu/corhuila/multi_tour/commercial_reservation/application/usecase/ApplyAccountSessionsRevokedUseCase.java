package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountSessionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ApplyAccountSessionsRevoked;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AccountSessionCutoffs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionCutoff;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionsRevoked;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;

// The cutoff and the event id commit or roll back as one (norm 5.3.11). An account belongs to one tenant or to none: an event that names
// another tenant than the one held is refused and leaves no id behind. The id is recorded for an older instant too, so the relay stops.
public final class ApplyAccountSessionsRevokedUseCase implements ApplyAccountSessionsRevoked {

    private final AccountSessionCutoffs cutoffs;
    private final ProcessedEvents processed;
    private final TenantEventTransaction transaction;

    public ApplyAccountSessionsRevokedUseCase(AccountSessionCutoffs cutoffs, ProcessedEvents processed, TenantEventTransaction transaction) {
        this.cutoffs = cutoffs;
        this.processed = processed;
        this.transaction = transaction;
    }

    @Override
    public AccountSessionOutcome apply(AccountSessionsRevoked event) {
        return transaction.inTransaction(() -> {
            if (processed.wasProcessed(event.eventId())) return AccountSessionOutcome.ALREADY_PROCESSED;
            var candidate = new AccountSessionCutoff(event.accountId(), event.tenantId(), event.revokedFrom());
            var held = cutoffs.find(event.accountId());
            if (held.isPresent() && event.tenantId() != null && held.get().tenantId() != null && !held.get().tenantId().equals(event.tenantId())) {
                throw new DomainException("tenantId", "is not the tenant of the account");
            }
            boolean applied = held.isEmpty() ? cutoffs.insertIfAbsent(candidate) || cutoffs.moveForward(candidate) : cutoffs.moveForward(candidate);
            processed.record(event.eventId());
            return applied ? AccountSessionOutcome.APPLIED : AccountSessionOutcome.UNCHANGED;
        });
    }
}
