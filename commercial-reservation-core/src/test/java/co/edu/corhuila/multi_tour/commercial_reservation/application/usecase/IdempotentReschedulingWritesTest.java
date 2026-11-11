package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RespondToReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// HU-COM-018 AC-18.6: the rescheduling writes run over the shared IdempotentWrites of the core, so the key belongs to one episode.
class IdempotentReschedulingWritesTest {
    private final UUID tenant = new UUID(1, 1), episodeId = new UUID(2, 1), otherEpisode = new UUID(2, 2), departure = new UUID(3, 1);
    private final PaymentActor actor = new PaymentActor(tenant, new UUID(4, 1), PaymentActor.Role.TENANT_ADMINISTRATOR);

    private final Map<String, UUID> keys = new HashMap<>();
    private final IdempotencyStore store = new IdempotencyStore() {
        @Override public Optional<UUID> find(UUID tenantId, String key) { return Optional.ofNullable(keys.get(key)); }
        @Override public boolean register(UUID tenantId, String key, String resourceType, UUID resourceId) { return keys.putIfAbsent(key, resourceId) == null; }
    };
    private final RequestTransaction transaction = new RequestTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) { return work.get(); }
    };

    private ReschedulingEpisode episode(UUID id) {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        return new ReschedulingEpisode(id, tenant, new UUID(5, 1), new UUID(6, 1), now, now, EpisodeState.OPEN, null, null, now, Duration.ZERO, List.of());
    }

    private IdempotentReschedulingWrites writes(int[] calls) {
        SendReschedulingProposal send = command -> { calls[0]++; return episode(command.episodeId()); };
        RespondToReschedulingProposal respond = new RespondToReschedulingProposal() {
            @Override public ReschedulingEpisode answerAsCustomer(PaymentActor a, UUID id, UUID proposalId, boolean accepted) { calls[0]++; return episode(id); }
            @Override public ReschedulingEpisode recordOutside(PaymentActor a, UUID id, UUID proposalId, boolean accepted, String channel, String evidence, Instant at) { calls[0]++; return episode(id); }
            @Override public ReschedulingEpisode visibleTo(PaymentActor a, UUID id) { return episode(id); }
        };
        return new IdempotentReschedulingWrites(send, respond, new IdempotentWrites(store, transaction));
    }

    @Test void aNewKeyWritesAndRegistersTheEpisode() {
        int[] calls = new int[1];

        var outcome = writes(calls).propose(actor, "key-1", episodeId, departure);

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.episode().id()).isEqualTo(episodeId);
        assertThat(keys).containsEntry("key-1", episodeId);
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test void aKeyUsedForAnotherEpisodeIsRefusedAndWritesNothing() {
        int[] calls = new int[1];
        keys.put("key-2", otherEpisode);

        assertThatThrownBy(() -> writes(calls).propose(actor, "key-2", episodeId, departure)).isInstanceOf(BusinessRuleViolation.class);
        assertThat(calls[0]).isZero();
    }
}
