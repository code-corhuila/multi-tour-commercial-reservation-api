package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.AnswerReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the use cases of HU-COM-018 have their own tests and the JDBC adapters their persistence test. These doubles keep the episodes in memory, apply the real domain moves of the
// episode and count how many times a write reached the use case, so the routes (roles, ownership, idempotency, status codes) can be tested without a database.
@TestConfiguration
class ReschedulingWritesHttpFixture {
    static final Instant NOW = TestJwtKeys.NOW;

    @Bean @Primary MemoryEpisodes memoryEpisodes() { return new MemoryEpisodes(); }

    @Bean @Primary SendReschedulingProposal memoryProposals(MemoryEpisodes episodes) {
        return command -> episodes.moved(command.episodeId(), command.tenantId(), episode -> episode.propose(UUID.randomUUID(), command.proposedSalidaId(), 3, command.actorId(), NOW,
                NOW.plusSeconds(864_000)));
    }

    @Bean @Primary AnswerReschedulingProposal memoryAcceptance(MemoryEpisodes episodes) {
        return command -> episodes.moved(command.episodeId(), command.tenantId(), episode -> episode.answer(command.toAnswer(), NOW));
    }

    @Bean @Primary ConcludeRescheduling memoryConclusion(MemoryEpisodes episodes) {
        return new ConcludeRescheduling() {
            public ReschedulingEpisode reject(AnswerProposalCommand command) { return episodes.moved(command.episodeId(), command.tenantId(), e -> e.answer(command.toAnswer(), NOW)); }
            public ReschedulingEpisode declareNoAlternative(UUID tenantId, UUID actorId, Role role, UUID episodeId) {
                return episodes.moved(episodeId, tenantId, e -> e.state() == co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState.CANCELLED ? e : e.declareNoAlternative(NOW));
            }
            public ReschedulingEpisode expire(UUID tenantId, UUID episodeId) { throw new UnsupportedOperationException(); }
        };
    }

    static final class MemoryEpisodes implements ReschedulingRepository {
        final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
        final List<UUID> writes = new ArrayList<>();

        void clear() { episodes.clear(); writes.clear(); }

        ReschedulingEpisode moved(UUID episodeId, UUID tenantId, java.util.function.UnaryOperator<ReschedulingEpisode> move) {
            ReschedulingEpisode moved = move.apply(find(tenantId, episodeId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId)));
            episodes.put(moved.id(), moved);
            writes.add(episodeId);
            return moved;
        }

        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) {
            return episodes.values().stream().filter(e -> e.tenantId().equals(tenantId) && e.reservationId().equals(reservationId) && e.originalSalidaId().equals(originalSalidaId)).findFirst();
        }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) {
            return Optional.ofNullable(episodes.get(episodeId)).filter(e -> e.tenantId().equals(tenantId));
        }
        @Override public void save(ReschedulingEpisode episode) { episodes.put(episode.id(), episode); }
    }
}
