package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Same mechanism as the creation of a reservation (norm 5.3.8, AC-04.11): a known key answers the original and writes nothing.
// The key names the reservation, which can only take one additional discount, so the stored record is the original.
public final class IdempotentAdditionalDiscountRegistration {
    public record Result(AdditionalDiscount discount, boolean created) {}
    static final String RESOURCE = "additional_discount";
    private final RegisterAdditionalDiscount register;
    private final AdditionalDiscountRepository discounts;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentAdditionalDiscountRegistration(RegisterAdditionalDiscount register, AdditionalDiscountRepository discounts,
            IdempotencyKeys keys, TransactionTemplate transactions) {
        this.register = register;
        this.discounts = discounts;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result register(DiscountRuleActor actor, RegisterAdditionalDiscount.Command command, String key) {
        UUID tenant = actor.tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Result(original(tenant, command.reservationId(), known.get()), false);
        Result result;
        try {
            result = transactions.execute(status -> {
                AdditionalDiscount discount = register.register(actor, command);
                if (keys.tryRegister(tenant, key, RESOURCE, command.reservationId())) return new Result(discount, true);
                status.setRollbackOnly(); // lost a race with the same key: discard this discount and its event
                return new Result(discount, false);
            });
        } catch (BusinessRuleViolation refused) {
            // The winner of a race with the same key may have committed between the first look and the write, so the
            // reservation already has its discount: if the key exists now, the answer is the stored one, not a refusal.
            var winner = keys.findResourceId(tenant, key);
            if (winner.isEmpty()) throw refused;
            return new Result(original(tenant, command.reservationId(), winner.get()), false);
        }
        if (result.created()) return result;
        var id = keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Result(original(tenant, command.reservationId(), id), false);
    }

    // A key belongs to one reservation: the same key for another one is a rule violation, not a replay.
    private AdditionalDiscount original(UUID tenant, UUID reservationId, UUID keyed) {
        if (!keyed.equals(reservationId)) {
            throw new BusinessRuleViolation("the Idempotency-Key was already used for another reservation");
        }
        return discounts.findByReservation(tenant, keyed).orElseThrow(() -> new ReservationNotFoundException(keyed));
    }
}
