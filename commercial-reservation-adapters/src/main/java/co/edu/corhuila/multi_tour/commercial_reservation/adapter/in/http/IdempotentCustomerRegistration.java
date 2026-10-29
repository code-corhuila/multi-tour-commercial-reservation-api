package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import org.springframework.transaction.support.TransactionTemplate;

public final class IdempotentCustomerRegistration {
    public record Result(Customer customer, boolean created) {}
    private final RegisterLeadCustomer register;
    private final GetCustomer get;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentCustomerRegistration(RegisterLeadCustomer register, GetCustomer get, IdempotencyKeys keys,
            TransactionTemplate transactions) {
        this.register = register;
        this.get = get;
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result register(CustomerActor actor, RegisterLeadCustomer.Command command, String key) {
        // A known key answers the original customer: nothing is written and no event is emitted.
        var known = keys.findResourceId(actor.tenantId(), key);
        if (known.isPresent()) return new Result(get.get(actor, known.get()), false);
        Result result = transactions.execute(status -> {
            Customer customer = register.register(actor, command);
            if (keys.tryRegister(actor.tenantId(), key, "customer", customer.customerId())) return new Result(customer, true);
            status.setRollbackOnly(); // lost a race with the same key: discard this insert and its event
            return new Result(customer, false);
        });
        if (result.created()) return result;
        var id = keys.findResourceId(actor.tenantId(), key)
                .orElseThrow(() -> new IllegalStateException("registered key has no resource"));
        return new Result(get.get(actor, id), false);
    }
}
