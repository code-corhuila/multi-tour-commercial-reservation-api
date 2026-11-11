package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Same mechanism as the customer registration (AC-07.14): a known key answers the original and writes nothing.
public final class IdempotentReservationCreation {
    public record Result(Reservation reservation, boolean created) {}
    private final CreateReservation create;
    private final ReservationQueries queries;
    private final CustomerByAccount accounts;
    private final CustomerRepository customers;
    private final IdempotentWrites writes;

    public IdempotentReservationCreation(CreateReservation create, ReservationQueries queries, CustomerByAccount accounts,
            CustomerRepository customers, IdempotentWrites writes) {
        this.create = create;
        this.queries = queries;
        this.accounts = accounts;
        this.customers = customers;
        this.writes = writes;
    }

    public Result create(CreateReservationCommand command, String key) {
        var done = writes.run(command.tenantId(), key, "reservation", () -> create.create(command), Reservation::reservationId,
                id -> original(command, id));
        return new Result(done.value(), done.created());
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
        if (command.holder() != null) return requireSameRequest(command, requireSameHolder(command, original));
        if (!original.customerId().equals(owner(command))) {
            throw new BusinessRuleViolation("the Idempotency-Key was already used by another customer");
        }
        return requireSameRequest(command, original);
    }

    // A holder has no id yet, so the key belongs to the holder whose normalized e-mail the stored customer carries.
    private Reservation requireSameHolder(CreateReservationCommand command, Reservation original) {
        String email = new ContactInfo(command.holder().email(), null).email();
        boolean same = customers.findByTenantAndId(command.tenantId(), original.customerId())
                .map(found -> found.contact().email() != null && found.contact().email().equals(email)).orElse(false);
        if (!same) throw new BusinessRuleViolation("the Idempotency-Key was already used by another customer");
        return original;
    }

    // The same key with another body is not a replay (AC-07.14): answering the first reservation would tell the caller it booked
    // what it did not ask for. The request is the departure, the tour, the people, the expected payment and the group.
    private static Reservation requireSameRequest(CreateReservationCommand command, Reservation original) {
        boolean same = original.departureId().equals(command.departureId())
                && original.service().serviceId().equals(command.serviceId())
                && original.people() == command.people()
                && original.expectedPaymentType().name().equals(command.paymentType())
                && original.expectedPaymentMethod().name().equals(command.paymentMethod())
                && documents(original.companions()).equals(documents(command.companions()));
        if (!same) throw new BusinessRuleViolation("the Idempotency-Key was already used with another request");
        return original;
    }

    private static Set<String> documents(List<Companion> companions) {
        return companions.stream().map(companion -> companion.documentNumber().comparable()).collect(Collectors.toSet());
    }
}
