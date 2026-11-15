package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;

// A known key answers the original customer: nothing is written and no event is emitted; the rule is IdempotentWrites'.
public final class IdempotentCustomerRegistration {
    public record Result(Customer customer, boolean created) {}
    private final RegisterLeadCustomer register;
    private final GetCustomer get;
    private final IdempotentWrites writes;

    public IdempotentCustomerRegistration(RegisterLeadCustomer register, GetCustomer get, IdempotentWrites writes) {
        this.register = register;
        this.get = get;
        this.writes = writes;
    }

    public Result register(CustomerActor actor, RegisterLeadCustomer.Command command, String key) {
        var done = writes.run(actor.tenantId(), key, "customer", () -> register.register(actor, command), Customer::customerId, id -> get.get(actor, id));
        return new Result(done.value(), done.created());
    }
}
