package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// The doubles of the follow-up and of the pending list of HU-COM-011: an in-memory history, a counter for the identifiers and a list
// that answers empty and records the question it was asked.
final class FollowupFakes implements PaymentFollowupRepository, PendingPaymentQueries, IdGenerator {

    final List<PaymentFollowup> followups = new ArrayList<>();
    final List<String> pendingQueries = new ArrayList<>();
    private long sequence = 1;

    @Override
    public UUID newId() {
        return new UUID(11, 0xf000 + sequence++);
    }

    @Override
    public void append(PaymentFollowup followup) {
        followups.add(followup);
    }

    @Override
    public FollowupPage page(UUID tenantId, UUID reservationId, int page, int limit) {
        List<PaymentFollowup> all = followups.stream()
                .filter(f -> f.tenantId().equals(tenantId) && f.reservationId().equals(reservationId))
                .sorted(Comparator.comparing(PaymentFollowup::contactedAt).reversed()).toList();
        return new FollowupPage(all.stream().skip((long) (page - 1) * limit).limit(limit).toList(), all.size());
    }

    @Override
    public PendingPaymentPage page(UUID tenantId, DeadlineKind kind, int page, int limit) {
        pendingQueries.add(tenantId + ":" + kind + ":" + page + ":" + limit);
        return new PendingPaymentPage(List.of(), 0);
    }
}
