package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// D-10: the values are bounded and a change carries a reason and its author. The read roles derive from Consult reservations
// (PROPOSED, the matrix has no payment-read row); the Final Customer never reads them. Nothing here touches a reservation.
public class ManagePaymentParametersUseCase implements ManagePaymentParameters {

    private static final Set<Role> READERS = Set.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER,
            Role.ANALYST, Role.ACCOUNTANT);

    private final PaymentParameterRepository parameters;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ManagePaymentParametersUseCase(PaymentParameterRepository parameters, PaymentTransaction transaction, Clock clock) {
        this.parameters = parameters;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public List<PaymentParameter> read(PaymentActor actor) {
        if (!READERS.contains(actor.role())) throw new PaymentAccessDeniedException();
        return parameters.findByTenant(actor.tenantId());
    }

    @Override
    public List<PaymentParameter> change(ChangePaymentParametersCommand command) {
        PaymentActor actor = command.actor();
        if (actor.role() != Role.TENANT_ADMINISTRATOR) throw new PaymentAccessDeniedException();
        if (command.parameters() == null || command.parameters().isEmpty())
            throw new DomainException("parameters", "at least one parameter row is required");
        Instant now = Instant.now(clock);
        List<PaymentParameter> changes = command.parameters().stream()
                .map(values -> new PaymentParameter(values, actor.actorId(), command.reason(), now)).toList();
        return transaction.inTransaction(() -> {
            changes.forEach(change -> parameters.save(actor.tenantId(), change));
            return changes;
        });
    }
}
