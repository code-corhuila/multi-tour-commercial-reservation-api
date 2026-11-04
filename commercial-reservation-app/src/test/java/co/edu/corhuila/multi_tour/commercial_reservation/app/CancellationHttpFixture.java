package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC record of HU-COM-013 has its own persistence test. The policy, the rescheduling mark and the financial decision
// are the doubles the service itself uses until their stories exist; here a test sets what they answer.
@TestConfiguration
class CancellationHttpFixture {
    @Bean @Primary MemoryCancellations memoryCancellations() { return new MemoryCancellations(); }

    static final class MemoryCancellations implements CancellationPolicyReader, ReprogrammingMarkReader, FinancialDecisionOpener, CancellationRecords {
        OptionalInt basisPoints = OptionalInt.empty();
        final Set<UUID> marked = new HashSet<>();
        final List<String> opened = new ArrayList<>();
        final Map<UUID, CancellationRecord> records = new HashMap<>();

        void clear() { basisPoints = OptionalInt.empty(); marked.clear(); opened.clear(); records.clear(); }
        public OptionalInt penaltyBasisPoints(UUID tenant, UUID service, Instant start, Instant now) { return basisPoints; }
        public boolean isMarked(UUID tenant, UUID reservation) { return marked.contains(reservation); }
        public void open(UUID tenant, UUID reservation, Cents base, Cents penalty, Instant at) { opened.add(base.amount() + "/" + penalty.amount()); }
        public void save(UUID tenant, CancellationRecord record) { records.put(record.reservationId(), record); }
        public Optional<CancellationRecord> find(UUID tenant, UUID reservation) { return Optional.ofNullable(records.get(reservation)); }
    }
}
