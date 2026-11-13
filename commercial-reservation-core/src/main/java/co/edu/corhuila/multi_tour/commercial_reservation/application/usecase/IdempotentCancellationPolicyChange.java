package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangeCancellationPoliciesCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCancellationPolicies;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Same mechanism as the other writes (norm 5.3.8): a known key with the same body answers and writes nothing; the same key with another body is a rule violation.
// LIMIT: the repetition returns the rows in force now, not a copy of the first answer.
public final class IdempotentCancellationPolicyChange {
    static final String RESOURCE = "cancellation-policies";
    private final ManageCancellationPolicies policies;
    private final IdempotentWrites writes;

    public IdempotentCancellationPolicyChange(ManageCancellationPolicies policies, IdempotentWrites writes) {
        this.policies = policies;
        this.writes = writes;
    }

    public List<CancellationPolicy> change(ChangeCancellationPoliciesCommand command, String key) {
        UUID fingerprint = UUID.nameUUIDFromBytes((command.reason() + "|" + command.policies()).getBytes(StandardCharsets.UTF_8));
        return writes.run(command.actor().tenantId(), key, RESOURCE, () -> policies.change(command), changed -> fingerprint,
                stored -> replay(command, stored, fingerprint)).value();
    }

    private List<CancellationPolicy> replay(ChangeCancellationPoliciesCommand command, UUID stored, UUID fingerprint) {
        if (!stored.equals(fingerprint)) throw new BusinessRuleViolation("the Idempotency-Key was already used with another body");
        return policies.read(command.actor());
    }
}
