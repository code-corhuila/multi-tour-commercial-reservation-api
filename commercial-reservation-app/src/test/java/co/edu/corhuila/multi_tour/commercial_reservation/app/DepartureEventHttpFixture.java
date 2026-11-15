package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the departure rows are exercised against the real engine in their own tests.
@TestConfiguration
class DepartureEventHttpFixture {
    @Bean @Primary MemoryDepartures memoryDepartures() { return new MemoryDepartures(); }

    static final class MemoryDepartures implements DepartureStateRepository, DepartureReader {
        final Map<UUID, DepartureState> rows = new HashMap<>();

        void clear() { rows.clear(); }
        public Optional<DepartureState> find(UUID tenantId, UUID departureId) {
            return Optional.ofNullable(rows.get(departureId)).filter(row -> row.tenantId().equals(tenantId));
        }
        public void save(DepartureState state) { rows.put(state.departureId(), state); }
        public List<DepartureState> findOpen(UUID tenantId, UUID serviceId, Instant now) {
            return rows.values().stream().filter(row -> row.tenantId().equals(tenantId) && row.serviceId().equals(serviceId)
                    && row.status() == DepartureStatus.SCHEDULED && row.startsAt().isAfter(now)).toList();
        }
    }
}
