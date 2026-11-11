package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ListProofsQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPaymentProofs;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentAttempt;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// D-08: the mark is derived at read time from the clock; the overdue filter asks the reader for the proofs presented at or
// before now minus 24 hours. Nothing is stored and no job flips a column. Paginated: page from 1, limit 1 to 100.
public class ListPaymentProofsUseCase implements ListPaymentProofs {

    private final PaymentProofReader reader;
    private final Clock clock;

    public ListPaymentProofsUseCase(PaymentProofReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    public ProofPage list(ListProofsQuery query) {
        PaymentProofAuthorization.requireRead(query.actor());
        ValidationStatus status = query.status() == null ? ValidationStatus.IN_VALIDATION : query.status();
        return page(query.actor().tenantId(), status, query.overdue(), query.page(), query.limit());
    }

    // Job 2 takes the tenant from the path; the service-token rule of that route is the adapter's.
    @Override
    public ProofPage overdueOf(UUID tenantId, int page, int limit) {
        return page(tenantId, ValidationStatus.IN_VALIDATION, true, page, limit);
    }

    private ProofPage page(UUID tenantId, ValidationStatus status, boolean overdueOnly, int page, int limit) {
        if (page < 1) throw new DomainException("page", "page starts at 1");
        if (limit < 1 || limit > 100) throw new DomainException("limit", "limit must be between 1 and 100");
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(PaymentAttempt.REVIEW_WINDOW);
        ProofSourcePage found = reader.find(tenantId, status, overdueOnly ? cutoff : null, page, limit);
        var items = found.items().stream().map(source -> ProofView.of(source.reservation(), source.attempt(), now)).toList();
        return new ProofPage(items, page, limit, found.total(), reader.countAtOrBefore(tenantId, ValidationStatus.IN_VALIDATION, cutoff));
    }
}
