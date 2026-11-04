package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDuePaymentDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// RNF-07: the instant comes from the clock port, never from the database.
public class ListDuePaymentDeadlinesUseCase implements ListDuePaymentDeadlines {

    private final DuePaymentQueries queries;
    private final Clock clock;

    public ListDuePaymentDeadlinesUseCase(DuePaymentQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    public DueDeadlinePage list(UUID tenantId, int page, int limit) {
        return queries.page(tenantId, Instant.now(clock), page, limit);
    }
}
