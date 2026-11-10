package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DuePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListDueSensitiveData;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationClosingReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RetentionAnchor;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-COM-019 plan D-03: the store only pre-filters (a bounded query by tenant and by an instant at least a year old); the pure function of the anchor decides which candidates
// are due. A reservation already deleted never comes back. The cursor is the id of the last candidate read, so a page can hold fewer rows than the limit and still continue.
public class ListDueSensitiveDataUseCase implements ListDueSensitiveData {

    private final RetentionFactsReader facts;
    private final ReservationClosingReader closings;
    private final RetentionDeletions deletions;
    private final Clock clock;

    public ListDueSensitiveDataUseCase(RetentionFactsReader facts, ReservationClosingReader closings, RetentionDeletions deletions, Clock clock) {
        this.facts = facts;
        this.closings = closings;
        this.deletions = deletions;
        this.clock = clock;
    }

    @Override
    public DuePage due(UUID tenantId, int limit, UUID after) {
        Instant now = Instant.now(clock);
        List<UUID> candidates = facts.dueCandidates(tenantId, now, limit, after);
        List<DuePage.Row> rows = new ArrayList<>();
        for (UUID reservationId : candidates) {
            if (deletions.isDeleted(tenantId, reservationId)) continue;
            Optional<RetentionFacts> own = facts.factsOf(tenantId, reservationId);
            if (own.isEmpty()) continue;
            RetentionFacts of = own.get();
            RetentionAnchor.of(of.departure(), of.reservation(), closings.closingOf(tenantId, reservationId), of.zone(), now).filter(anchor -> anchor.isDue(now, of.zone()))
                    .ifPresent(anchor -> rows.add(new DuePage.Row(reservationId, of.customerId(), anchor.basis(), anchor.at())));
        }
        return new DuePage(rows, candidates.size() == limit ? candidates.get(candidates.size() - 1) : null);
    }
}
