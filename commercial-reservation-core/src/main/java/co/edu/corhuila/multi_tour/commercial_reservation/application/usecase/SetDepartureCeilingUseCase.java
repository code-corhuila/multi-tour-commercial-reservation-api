package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CeilingState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureCeiling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SetDepartureCeiling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureCeilings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

// The ceiling is the effective capacity of the departure. The row is locked, so the held seats cannot grow between the check and the write; a refusal
// changes nothing and spends no key. The key is registered in the same transaction as the write, with a second row that keeps what was answered
// (the previous ceiling and the new one, packed in a uuid), because a replay must answer the original previous ceiling: the saga undoes its step with it.
// A replay never writes again, so a ceiling restored in between stays restored.
public class SetDepartureCeilingUseCase implements SetDepartureCeiling {
    static final int MAXIMUM = 100_000;
    private static final String DEPARTURE_TYPE = "capacity-ceiling";
    private static final String ANSWER_TYPE = "capacity-ceiling-answer";
    private static final String REUSED = "the Idempotency-Key was already used by another request";

    // Thrown inside the transaction when the key is taken, so the transaction ends without writing anything.
    private static final class KeyTaken extends RuntimeException {
        KeyTaken() { super(null, null, false, false); }
    }

    private final DepartureCeilings ceilings;
    private final IdempotencyStore keys;
    private final PaymentTransaction transaction;

    public SetDepartureCeilingUseCase(DepartureCeilings ceilings, IdempotencyStore keys, PaymentTransaction transaction) {
        this.ceilings = ceilings;
        this.keys = keys;
        this.transaction = transaction;
    }

    @Override
    public DepartureCeiling set(UUID tenantId, UUID salidaId, int ceiling, String key) {
        if (ceiling < 0 || ceiling > MAXIMUM) throw new DomainException("ceiling", "ceiling must be between 0 and " + MAXIMUM);
        Optional<UUID> known = keys.find(tenantId, key);
        if (known.isPresent()) return replay(tenantId, salidaId, ceiling, key, known.get());
        try {
            return transaction.inTransaction(() -> {
                if (!keys.register(tenantId, key, DEPARTURE_TYPE, salidaId)) throw new KeyTaken();
                CeilingState state = ceilings.findForUpdate(tenantId, salidaId).orElseThrow(() -> new DepartureNotFoundException(salidaId));
                if (!state.open()) throw new BusinessRuleViolation("the departure is final and its ceiling cannot change");
                if (state.seatsHeld() > ceiling) {
                    throw new BusinessRuleViolation("the ceiling " + ceiling + " is lower than the " + state.seatsHeld() + " seats held");
                }
                if (state.ceiling() != ceiling) ceilings.setCeiling(tenantId, salidaId, ceiling);
                keys.register(tenantId, answerKey(key), ANSWER_TYPE, pack(state.ceiling(), ceiling));
                return new DepartureCeiling(state.ceiling(), ceiling, state.seatsHeld());
            });
        } catch (KeyTaken lost) {
            UUID winner = keys.find(tenantId, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
            return replay(tenantId, salidaId, ceiling, key, winner);
        }
    }

    private DepartureCeiling replay(UUID tenantId, UUID salidaId, int ceiling, String key, UUID stored) {
        if (!stored.equals(salidaId)) throw new BusinessRuleViolation(REUSED);
        UUID answer = keys.find(tenantId, answerKey(key)).orElseThrow(() -> new BusinessRuleViolation(REUSED));
        int previous = (int) (answer.getMostSignificantBits());
        int original = (int) (answer.getLeastSignificantBits());
        if (original != ceiling) throw new BusinessRuleViolation(REUSED);
        CeilingState now = ceilings.find(tenantId, salidaId).orElseThrow(() -> new DepartureNotFoundException(salidaId));
        return new DepartureCeiling(previous, original, now.seatsHeld());
    }

    private static UUID pack(int previous, int ceiling) {
        return new UUID(previous, ceiling);
    }

    // The key of the second row: a digest, so it keeps the 8 to 128 characters of the table whatever the length of the key.
    private static String answerKey(String key) {
        try {
            return "cc:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
