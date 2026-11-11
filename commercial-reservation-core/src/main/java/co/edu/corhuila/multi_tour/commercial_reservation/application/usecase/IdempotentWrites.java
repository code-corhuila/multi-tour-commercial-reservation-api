package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

// Norm 5.3.8, RNF-06: a known key answers the stored resource and writes nothing and emits nothing. The key is registered in the same
// transaction as the write, so a refusal leaves no key and a request that lost a race with the same key is rolled back with its events and
// answers the winner's resource.
public final class IdempotentWrites {
    public record Outcome<T>(T value, boolean created) {}

    // Thrown inside the transaction when the key is taken, so the transaction ends without writing anything.
    private static final class KeyTaken extends RuntimeException {
        KeyTaken() { super(null, null, false, false); }
    }

    private final IdempotencyStore keys;
    private final RequestTransaction transaction;

    public IdempotentWrites(IdempotencyStore keys, RequestTransaction transaction) {
        this.keys = keys;
        this.transaction = transaction;
    }

    public <T> Outcome<T> run(UUID tenant, String key, String resourceType, Supplier<T> work, Function<T, UUID> idOf, Function<UUID, T> stored) {
        Optional<UUID> known = keys.find(tenant, key);
        if (known.isPresent()) return new Outcome<>(stored.apply(known.get()), false);
        try {
            return transaction.inTransaction(() -> {
                T created = work.get();
                if (!keys.register(tenant, key, resourceType, idOf.apply(created))) throw new KeyTaken();
                return new Outcome<>(created, true);
            });
        } catch (KeyTaken lost) {
            // Resolved after the rollback: the discarded insert must not survive a retry.
            UUID winner = keys.find(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
            return new Outcome<>(stored.apply(winner), false);
        }
    }
}
