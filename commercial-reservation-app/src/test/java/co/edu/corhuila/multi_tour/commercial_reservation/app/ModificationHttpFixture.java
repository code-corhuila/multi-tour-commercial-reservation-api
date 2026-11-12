package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PendingDifferenceDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of HU-COM-012 have their own persistence test. The terms, the pending-difference row, the history and the
// decision of the excess are what the service itself uses; here a test sets what they answer.
@TestConfiguration
class ModificationHttpFixture {
    @Bean @Primary MemoryModifications memoryModifications() { return new MemoryModifications(); }

    static final class MemoryModifications implements ChangePolicyReader, PendingDifferenceDeadlines, ReservationModifications, ExcessDecisionOpener {
        Optional<ChangePolicy> policy = Optional.of(new ChangePolicy(true, 60));
        final List<ReservationModificationRecord> rows = new ArrayList<>();
        final List<Instant> differences = new ArrayList<>();
        final List<Long> excesses = new ArrayList<>();

        void clear() { policy = Optional.of(new ChangePolicy(true, 60)); rows.clear(); differences.clear(); excesses.clear(); }
        public Optional<ChangePolicy> policyFor(UUID tenant, UUID service, ChangeType type) { return policy; }
        public OptionalInt balanceAdvanceHours(UUID tenant, UUID service, PaymentType type) { return OptionalInt.of(48); }
        public void record(UUID tenant, UUID reservation, Instant dueAt) { differences.add(dueAt); }
        public void open(UUID tenant, UUID reservation, Cents excess, Instant at) { excesses.add(excess.amount()); }
        public void append(UUID tenant, ReservationModificationRecord record) { rows.add(record); }
        public Optional<ReservationModificationRecord> find(UUID tenant, UUID id) {
            return rows.stream().filter(row -> row.modificationId().equals(id)).findFirst();
        }
        public ModificationPage page(UUID tenant, UUID reservation, int page, int limit) {
            var newestFirst = new ArrayList<>(rows);
            java.util.Collections.reverse(newestFirst);
            var mine = newestFirst.stream().filter(row -> row.reservationId().equals(reservation))
                    .sorted(Comparator.comparing(ReservationModificationRecord::registeredAt).reversed()).toList();
            return new ModificationPage(mine.stream().skip((long) (page - 1) * limit).limit(limit).toList(), mine.size());
        }
    }
}
