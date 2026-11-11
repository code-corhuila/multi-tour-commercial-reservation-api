package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the use cases of HU-COM-018 have their own tests and the JDBC adapters their persistence test. These doubles keep the episodes in memory, apply the real
// domain moves (open, void, expire) and let a test plant the known reservations and the due list the job reads.
@TestConfiguration
class ReschedulingHttpFixture {
    @Bean @Primary MemoryEpisodes memoryEpisodes() { return new MemoryEpisodes(); }

    @Bean @Primary OpenRescheduling memoryOpening(MemoryEpisodes episodes) {
        return command -> {
            if (!episodes.reservations.contains(command.reservationId())) throw new ReservationNotFoundException(command.reservationId());
            return episodes.findByIncident(command.tenantId(), command.reservationId(), command.departureId()).orElseGet(() -> {
                ReschedulingEpisode episode = ReschedulingEpisode.open(UUID.randomUUID(), command.tenantId(), command.reservationId(), command.departureId(), command.cancelledAt(),
                        TestJwtKeys.NOW);
                episodes.save(episode);
                return episode;
            });
        };
    }

    @Bean @Primary VoidReschedulingProposals memoryVoiding(MemoryEpisodes episodes) {
        return (tenantId, salidaId, cancelledAt) -> {
            List<UUID> changed = new ArrayList<>();
            for (ReschedulingEpisode episode : episodes.findPendingOn(tenantId, salidaId)) {
                episodes.save(episode.voidPending(cancelledAt));
                changed.add(episode.id());
            }
            return changed;
        };
    }

    @Bean @Primary ConcludeRescheduling memoryConclusion(MemoryEpisodes episodes) {
        return new ConcludeRescheduling() {
            public ReschedulingEpisode reject(AnswerProposalCommand command) { throw new UnsupportedOperationException(); }
            public ReschedulingEpisode declareNoAlternative(UUID tenantId, UUID actorId, Role role, UUID episodeId) { throw new UnsupportedOperationException(); }
            public ReschedulingEpisode expire(UUID tenantId, UUID episodeId) {
                ReschedulingEpisode episode = episodes.find(tenantId, episodeId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
                if (episode.state() == EpisodeState.CANCELLED) return episode;
                ReschedulingEpisode closed = episode.expire(TestJwtKeys.NOW);
                episodes.save(closed);
                return closed;
            }
        };
    }

    @Bean @Primary ReschedulingQueries memoryDue(MemoryEpisodes episodes) {
        return (now, limit) -> episodes.episodes.values().stream().filter(e -> e.state() == EpisodeState.OPEN && !now.isBefore(e.proposalPeriodEnd()) && e.pendingProposal().isEmpty())
                .limit(limit).map(e -> new DueRescheduling(e.tenantId(), e.reservationId(), e.id())).toList();
    }

    static final class MemoryEpisodes implements ReschedulingRepository {
        final Map<UUID, ReschedulingEpisode> episodes = new LinkedHashMap<>();
        final List<UUID> reservations = new ArrayList<>();

        void clear() { episodes.clear(); reservations.clear(); }

        public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID salidaId) {
            return episodes.values().stream().filter(e -> e.tenantId().equals(tenantId) && e.reservationId().equals(reservationId) && e.originalSalidaId().equals(salidaId)).findFirst();
        }
        public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) {
            return Optional.ofNullable(episodes.get(episodeId)).filter(e -> e.tenantId().equals(tenantId));
        }
        public void save(ReschedulingEpisode episode) { episodes.put(episode.id(), episode); }
        public List<ReschedulingEpisode> findPendingOn(UUID tenantId, UUID salidaId) {
            return episodes.values().stream().filter(e -> e.tenantId().equals(tenantId) && e.state() == EpisodeState.OPEN
                    && e.pendingProposal().filter(p -> p.salidaId().equals(salidaId)).isPresent()).toList();
        }
    }
}
