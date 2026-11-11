package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Same mechanism as the customer registration (AC-07.14): a known key answers the original and writes nothing.
public final class IdempotentReservationCreation {
    public record Result(Reservation reservation, boolean created) {}
    private final CreateReservation create;
    private final ReservationQueries queries;
    private final CustomerByAccount accounts;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentReservationCreation(CreateReservation create, ReservationQueries queries, CustomerByAccount accounts, IdempotencyKeys keys,
            TransactionTemplate transactions) {
        this.create = create;
        this.queries = queries;
        this.accounts = accounts;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result create(CreateReservationCommand command, String key) {
        UUID tenant = command.tenantId();
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return new Result(original(command, known.get()), false);
        Result result = transactions.execute(status -> {
            Reservation reservation = create.create(command);
            if (keys.tryRegister(tenant, key, "reservation", reservation.reservationId())) return new Result(reservation, true);
            status.setRollbackOnly(); // lost a race with the same key: discard this reservation and its event
            return new Result(reservation, false);
        });
        if (result.created()) return result;
        var id = keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Result(original(command, id), false);
    }

    // The customer of the command: the named one, or for a Final Customer the one linked to their account (AC-08.17).
    private UUID owner(CreateReservationCommand command) {
        if (command.ownerAccountId() == null) return command.customerId();
        return accounts.findByTenantAndAccount(command.tenantId(), command.ownerAccountId()).map(found -> found.customerId()).orElse(null);
    }

    // A key belongs to one customer: the same key for another customer is a rule violation, not a replay.
    private Reservation original(CreateReservationCommand command, UUID reservationId) {
        Reservation original = queries.findByTenantAndId(command.tenantId(), reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!original.customerId().equals(owner(command))) {
            throw new BusinessRuleViolation("the Idempotency-Key was already used by another customer");
        }
        return original;
    }
}
