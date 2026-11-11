package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// The shared rule of the idempotent writes, with fakes: the key is registered with the write, a replay writes nothing, a refusal leaves no key.
class IdempotentWritesTest {
    private final UUID tenant = new UUID(1, 1), first = new UUID(2, 1), second = new UUID(2, 2);

    // A store whose writes are visible after the transaction commits and discarded when it throws.
    private static final class Fakes implements IdempotencyStore, RequestTransaction {
        final Map<String, UUID> committed = new HashMap<>(), pending = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        UUID rival;

        @Override public Optional<UUID> find(UUID tenantId, String key) { return Optional.ofNullable(committed.get(key)); }

        @Override public boolean register(UUID tenantId, String key, String resourceType, UUID resourceId) {
            if (rival != null) { committed.put(key, rival); return false; }
            return committed.containsKey(key) ? false : pending.put(key, resourceId) == null;
        }

        @Override public <T> T inTransaction(Supplier<T> work) {
            try {
                T result = work.get();
                committed.putAll(pending);
                return result;
            } finally {
                pending.clear();
            }
        }
    }

    private IdempotentWrites.Outcome<UUID> run(Fakes fakes, UUID produced, String key) {
        return new IdempotentWrites(fakes, fakes).run(tenant, key, "thing", () -> { fakes.writes.add(produced.toString()); return produced; }, id -> id, id -> id);
    }

    @Test void aNewKeyRunsTheWriteAndRegistersTheKeyWithTheResource() {
        var fakes = new Fakes();

        var done = run(fakes, first, "key-1");

        assertThat(done.created()).isTrue();
        assertThat(done.value()).isEqualTo(first);
        assertThat(fakes.committed).containsEntry("key-1", first);
    }

    @Test void aKnownKeyAnswersTheStoredResourceAndWritesNothing() {
        var fakes = new Fakes();
        run(fakes, first, "key-1");

        var again = run(fakes, second, "key-1");

        assertThat(again.created()).isFalse();
        assertThat(again.value()).isEqualTo(first);
        assertThat(fakes.writes).containsExactly(first.toString());
    }

    @Test void aKeyLostInARaceDiscardsThisWriteAndAnswersTheWinner() {
        var fakes = new Fakes();
        fakes.rival = second;

        var done = run(fakes, first, "key-1");

        assertThat(done.created()).isFalse();
        assertThat(done.value()).isEqualTo(second);
        assertThat(fakes.pending).isEmpty();
    }

    @Test void aRefusedWriteLeavesNoKeyBehind() {
        var fakes = new Fakes();
        var writes = new IdempotentWrites(fakes, fakes);

        assertThatThrownBy(() -> writes.run(tenant, "key-1", "thing", () -> { throw new IllegalStateException("refused"); }, id -> first, id -> first))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fakes.committed).isEmpty();
    }
}
