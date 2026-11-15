package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExecutionChange;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ChangeDepartureExecution;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureExecutionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

// RN-EJE-001, CA-029. Every reservation of the departure is locked, moved by the domain (Reservation.startExecution or completeExecution) and saved in one
// transaction, so a failure leaves all as they were. The key is registered in the same transaction with a second row that keeps the counts (as the ceiling
// route does), so a replay answers the original counts and never writes again. No event is written: the catalog of events has none for this change (spec 025
// DP-DEX-1). Nothing personal is read or logged.
public class ChangeDepartureExecutionUseCase implements ChangeDepartureExecution {
    private static final String ANSWER_TYPE = "departure-execution-answer";
    private static final String REUSED = "the Idempotency-Key was already used by another request";

    // Thrown inside the transaction when the key is taken, so the transaction ends without writing anything.
    private static final class KeyTaken extends RuntimeException {
        KeyTaken() { super(null, null, false, false); }
    }

    private enum Kind {
        START("execution-start", "es:", status -> status == DepartureStatus.FINALIZED || status == DepartureStatus.CANCELLED,
                "the departure is final and cannot start", Reservation::startExecution),
        END("execution-end", "ee:", status -> status == DepartureStatus.CANCELLED, "the departure is cancelled and cannot end", Reservation::completeExecution);

        final String resourceType, answerPrefix, refusal;
        final Predicate<DepartureStatus> refused;
        final Predicate<Reservation> move;

        Kind(String resourceType, String answerPrefix, Predicate<DepartureStatus> refused, String refusal, Predicate<Reservation> move) {
            this.resourceType = resourceType;
            this.answerPrefix = answerPrefix;
            this.refused = refused;
            this.refusal = refusal;
            this.move = move;
        }
    }

    private final DepartureExecutionReader departures;
    private final ReservationRepository reservations;
    private final IdempotencyStore keys;
    private final PaymentTransaction transaction;

    public ChangeDepartureExecutionUseCase(DepartureExecutionReader departures, ReservationRepository reservations, IdempotencyStore keys,
            PaymentTransaction transaction) {
        this.departures = departures;
        this.reservations = reservations;
        this.keys = keys;
        this.transaction = transaction;
    }

    @Override
    public ExecutionChange start(UUID tenantId, UUID salidaId, String key) {
        return run(Kind.START, tenantId, salidaId, key);
    }

    @Override
    public ExecutionChange end(UUID tenantId, UUID salidaId, String key) {
        return run(Kind.END, tenantId, salidaId, key);
    }

    private ExecutionChange run(Kind kind, UUID tenantId, UUID salidaId, String key) {
        Optional<UUID> known = keys.find(tenantId, key);
        if (known.isPresent()) return replay(kind, tenantId, salidaId, key, known.get());
        try {
            return transaction.inTransaction(() -> {
                if (!keys.register(tenantId, key, kind.resourceType, salidaId)) throw new KeyTaken();
                DepartureStatus status = departures.statusOf(tenantId, salidaId).orElseThrow(() -> new DepartureNotFoundException(salidaId));
                if (kind.refused.test(status)) throw new BusinessRuleViolation(kind.refusal);
                List<UUID> ids = departures.reservationIds(tenantId, salidaId);
                int changed = 0;
                for (UUID id : ids) {
                    Optional<Reservation> locked = reservations.lockByTenantAndId(tenantId, id);
                    if (locked.isPresent() && kind.move.test(locked.get())) {
                        reservations.save(locked.get());
                        changed++;
                    }
                }
                keys.register(tenantId, answerKey(kind, key), ANSWER_TYPE, new UUID(changed, ids.size() - changed));
                return new ExecutionChange(salidaId, changed, ids.size() - changed);
            });
        } catch (KeyTaken lost) {
            UUID winner = keys.find(tenantId, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
            return replay(kind, tenantId, salidaId, key, winner);
        }
    }

    // A key belongs to one departure and one of the two routes: any other use finds no answer of its own and is refused.
    private ExecutionChange replay(Kind kind, UUID tenantId, UUID salidaId, String key, UUID stored) {
        if (!stored.equals(salidaId)) throw new BusinessRuleViolation(REUSED);
        UUID answer = keys.find(tenantId, answerKey(kind, key)).orElseThrow(() -> new BusinessRuleViolation(REUSED));
        return new ExecutionChange(salidaId, (int) answer.getMostSignificantBits(), (int) answer.getLeastSignificantBits());
    }

    // The key of the second row: a digest, so it keeps the 8 to 128 characters of the table whatever the length of the key.
    private static String answerKey(Kind kind, String key) {
        try {
            return kind.answerPrefix + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
