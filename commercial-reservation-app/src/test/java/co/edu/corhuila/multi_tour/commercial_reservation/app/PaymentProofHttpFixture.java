package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSource;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the list of the proofs reads the reservations the HTTP tests keep in memory. The query of the real adapter has its
// own persistence test.
@TestConfiguration
class PaymentProofHttpFixture {
    @Bean @Primary PaymentProofReader memoryProofReader(ReservationHttpFixture.MemoryReservations store) {
        return new PaymentProofReader() {
            private List<ProofSource> all(UUID tenant, ValidationStatus status, Instant cutoff) {
                return store.reservations.values().stream().filter(found -> found.tenantId().equals(tenant))
                        .flatMap(found -> found.attempts().stream().filter(attempt -> attempt.validationStatus() == status)
                                .filter(attempt -> cutoff == null || !attempt.registeredAt().isAfter(cutoff))
                                .map(attempt -> new ProofSource(found, attempt)))
                        .sorted(Comparator.comparing((ProofSource source) -> source.attempt().registeredAt())).toList();
            }
            @Override public ProofSourcePage find(UUID tenant, ValidationStatus status, Instant cutoff, int page, int limit) {
                var rows = all(tenant, status, cutoff);
                return new ProofSourcePage(rows.stream().skip((page - 1L) * limit).limit(limit).toList(), rows.size());
            }
            @Override public long countAtOrBefore(UUID tenant, ValidationStatus status, Instant cutoff) {
                return all(tenant, status, cutoff).size();
            }
        };
    }
}
