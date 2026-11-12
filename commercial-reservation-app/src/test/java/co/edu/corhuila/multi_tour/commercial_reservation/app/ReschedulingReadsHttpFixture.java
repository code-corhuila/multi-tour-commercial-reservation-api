package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the episodes are exercised by ReschedulingReadsPersistenceTest. The reads apply the same conditions as the SQL (the tenant always, the customer
// when the reader is a Final Customer), so the routes can be tested without a database.
@TestConfiguration
class ReschedulingReadsHttpFixture {
    @Bean @Primary MemoryEpisodeReads memoryEpisodeReads() { return new MemoryEpisodeReads(); }

    static final class MemoryEpisodeReads implements ReschedulingReads {
        final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
        final Map<UUID, UUID> holders = new LinkedHashMap<>();

        void clear() { episodes.clear(); holders.clear(); }

        void plant(ReschedulingEpisode episode, UUID customerId) { episodes.put(episode.id(), episode); holders.put(episode.id(), customerId); }

        private Optional<ReschedulingEpisode> episode(UUID tenantId, UUID episodeId) {
            return Optional.ofNullable(episodes.get(episodeId)).filter(e -> e.tenantId().equals(tenantId));
        }

        @Override public RefundPage<ReschedulingView> page(UUID tenantId, UUID customerId, UUID reservationId, UUID departureId, EpisodeState state, int page, int limit) {
            var found = episodes.values().stream().filter(e -> e.tenantId().equals(tenantId) && (customerId == null || customerId.equals(holders.get(e.id())))
                    && (reservationId == null || reservationId.equals(e.reservationId())) && (departureId == null || departureId.equals(e.originalSalidaId()))
                    && (state == null || state == e.state())).map(e -> new ReschedulingView(e, holders.get(e.id()))).toList();
            return new RefundPage<>(found.stream().skip((long) (page - 1) * limit).limit(limit).toList(), found.size());
        }
        @Override public Optional<ReschedulingView> find(UUID tenantId, UUID customerId, UUID episodeId) {
            return episode(tenantId, episodeId).filter(e -> customerId == null || customerId.equals(holders.get(e.id()))).map(e -> new ReschedulingView(e, holders.get(e.id())));
        }
    }
}
