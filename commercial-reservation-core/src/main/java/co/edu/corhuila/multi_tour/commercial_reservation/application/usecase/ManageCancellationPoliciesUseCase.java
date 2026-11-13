package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangeCancellationPoliciesCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCancellationPolicies;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicyValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// HU-COM-013 DP-02: the role is checked before anything else (the Administrator writes, the staff reads), then the reason and each row, then the services of the tenant;
// the rows are written in one transaction so a refused row leaves none.
public class ManageCancellationPoliciesUseCase implements ManageCancellationPolicies {

    private static final UUID TENANT_LEVEL = new UUID(0, 0);

    private final CancellationPolicyRepository policies;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ManageCancellationPoliciesUseCase(CancellationPolicyRepository policies, PaymentTransaction transaction, Clock clock) {
        this.policies = policies;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public List<CancellationPolicy> read(PaymentActor actor) {
        CancellationAuthorization.requireRead(actor);
        return policies.findByTenant(actor.tenantId());
    }

    @Override
    public List<CancellationPolicy> change(ChangeCancellationPoliciesCommand command) {
        PaymentActor actor = command.actor();
        CancellationAuthorization.requireChangePolicy(actor);
        if (command.reason() == null || command.reason().isBlank()) throw new DomainException("reason", "reason is required");
        if (command.policies() == null || command.policies().isEmpty()) throw new DomainException("policies", "at least one policy row is required");
        Instant now = Instant.now(clock);
        Set<UUID> seen = new HashSet<>();
        for (CancellationPolicyValues values : command.policies()) {
            if (values.penaltyBasisPoints() == null) throw new DomainException("penaltyBasisPoints", "penaltyBasisPoints is required");
            if (!seen.add(values.serviceId() == null ? TENANT_LEVEL : values.serviceId())) {
                throw new DomainException("serviceId", "a service appears twice in the same change");
            }
        }
        List<CancellationPolicy> changes = command.policies().stream()
                .map(values -> new CancellationPolicy(values.serviceId(), values.penaltyBasisPoints(), actor.actorId(), command.reason().strip(), now)).toList();
        return transaction.inTransaction(() -> {
            for (CancellationPolicy change : changes) {
                if (change.serviceId() != null && !policies.serviceExists(actor.tenantId(), change.serviceId())) {
                    throw new CatalogServiceNotFoundException(change.serviceId());
                }
            }
            changes.forEach(change -> policies.save(actor.tenantId(), change));
            return changes;
        });
    }
}
