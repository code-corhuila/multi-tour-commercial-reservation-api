package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.FollowupPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PendingPaymentPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueDeadlinePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DuePaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialDeadlineRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingPaymentQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the deadline, the follow-up and the pending list have their own persistence test. The extension is
// written on the single deadline the payment fake keeps, so a test reads back what the routes wrote.
@TestConfiguration
class PaymentDeadlineHttpFixture {
    @Bean @Primary MemoryDeadlines memoryDeadlines(PaymentHttpFixture.MemoryPayments payments) { return new MemoryDeadlines(payments); }

    static final class MemoryDeadlines implements PaymentExtensionPolicy, PaymentDeadlineWriter, PaymentFollowupRepository, PendingPaymentQueries, DuePaymentQueries, InitialWaitPolicy, InitialDeadlineRecorder {
        private final PaymentHttpFixture.MemoryPayments payments;
        final List<PaymentFollowup> followups = new ArrayList<>();
        final List<String> asked = new ArrayList<>();
        final List<DeadlineView> due = new ArrayList<>();
        Integer maximum = 48;
        Integer initialWait;
        final List<String> initial = new ArrayList<>();

        MemoryDeadlines(PaymentHttpFixture.MemoryPayments payments) { this.payments = payments; }

        void clear() { followups.clear(); asked.clear(); due.clear(); maximum = 48; initialWait = null; initial.clear(); }
        public Optional<Integer> maximumHours(UUID tenant, Reservation reservation) { return Optional.ofNullable(maximum); }
        public void saveExtension(UUID tenant, UUID reservation, int hours, Instant at, UUID by) {
            PaymentDeadline now = payments.deadline;
            payments.deadline = new PaymentDeadline(now.kind(), now.dueAt(), hours, at, by, now.frozenAt(), now.remainingAtFreezeMinutes());
        }
        public void append(PaymentFollowup followup) { followups.add(followup); }
        public Optional<PaymentFollowup> find(UUID tenant, UUID followupId) {
            return followups.stream().filter(f -> f.tenantId().equals(tenant) && f.followupId().equals(followupId)).findFirst();
        }
        public FollowupPage page(UUID tenant, UUID reservation, int page, int limit) {
            var all = followups.stream().filter(f -> f.tenantId().equals(tenant) && f.reservationId().equals(reservation))
                    .sorted(Comparator.comparing(PaymentFollowup::contactedAt).reversed()).toList();
            return new FollowupPage(all.stream().skip((page - 1L) * limit).limit(limit).toList(), all.size());
        }
        public DueDeadlinePage page(UUID tenant, Instant now, int page, int limit) {
            asked.add("due:" + tenant + ":" + now + ":" + page + ":" + limit);
            return new DueDeadlinePage(due.stream().skip((page - 1L) * limit).limit(limit).toList(), due.size());
        }
        public Optional<Integer> initialWaitHours(UUID tenant, Reservation reservation) { return Optional.ofNullable(initialWait); }
        public void record(UUID tenant, UUID reservation, Instant dueAt) { initial.add(reservation + ":" + dueAt); }
        public PendingPaymentPage page(UUID tenant, DeadlineKind kind, int page, int limit) {
            asked.add(tenant + ":" + kind + ":" + page + ":" + limit);
            return new PendingPaymentPage(List.of(), 0);
        }
    }
}
