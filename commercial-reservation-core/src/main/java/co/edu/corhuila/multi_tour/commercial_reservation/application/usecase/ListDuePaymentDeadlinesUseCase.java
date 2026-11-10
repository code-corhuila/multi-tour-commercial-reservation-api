package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDuePaymentDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// RNF-07: the instant comes from the clock port, never from the database.
public class ListDuePaymentDeadlinesUseCase implements ListDuePaymentDeadlines {

    private final DuePaymentQueries queries;
    private final Clock clock;
    private final TenantStatusReader statuses;

    public ListDuePaymentDeadlinesUseCase(DuePaymentQueries queries, Clock clock) {
        this(queries, clock, tenantId -> java.util.Optional.empty());
    }

    // HU-COM-020 RN-01, AC-20.1: the status is read at the call; an inactive tenant has no due deadline, and an unknown one is active. A failed read is thrown.
    public ListDuePaymentDeadlinesUseCase(DuePaymentQueries queries, Clock clock, TenantStatusReader statuses) {
        this.queries = queries;
        this.clock = clock;
        this.statuses = statuses;
    }

    @Override
    public DueDeadlinePage list(UUID tenantId, int page, int limit) {
        if (statuses.statusOf(tenantId).filter(status -> status == TenantStatus.INACTIVE).isPresent()) return new DueDeadlinePage(java.util.List.of(), 0);
        return queries.page(tenantId, Instant.now(clock), page, limit);
    }
}
