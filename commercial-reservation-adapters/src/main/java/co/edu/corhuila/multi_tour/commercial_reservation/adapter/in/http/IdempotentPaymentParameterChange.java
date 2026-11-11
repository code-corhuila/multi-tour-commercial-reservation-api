package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Same mechanism as the other writes (norm 5.3.8): a known key with the same body answers and writes nothing; the same key with
// another body is a rule violation. The key store keeps one id per key, so the id is derived from the body. LIMIT: the repetition
// returns the rows in force now, not a copy of the first answer, so a change made in between shows in it.
public final class IdempotentPaymentParameterChange {
    static final String RESOURCE = "payment-parameters";
    private final ManagePaymentParameters parameters;
    private final IdempotencyKeys keys;
    private final TransactionTemplate transactions;

    public IdempotentPaymentParameterChange(ManagePaymentParameters parameters, IdempotencyKeys keys, TransactionTemplate transactions) {
        this.parameters = parameters;
        this.keys = keys;
        this.transactions = transactions;
    }

    @SuppressWarnings("unchecked")
    public List<PaymentParameter> change(ChangePaymentParametersCommand command, String key) {
        UUID tenant = command.actor().tenantId();
        UUID fingerprint = UUID.nameUUIDFromBytes((command.reason() + "|" + command.parameters()).getBytes(StandardCharsets.UTF_8));
        var known = keys.findResourceId(tenant, key);
        if (known.isPresent()) return replay(command, known.get(), fingerprint);
        List<PaymentParameter>[] changed = new List[1];
        Boolean registered = transactions.execute(status -> {
            changed[0] = parameters.change(command);
            if (keys.tryRegister(tenant, key, RESOURCE, fingerprint)) return true;
            status.setRollbackOnly(); // lost a race with the same key: discard this change and its event
            return false;
        });
        if (Boolean.TRUE.equals(registered)) return changed[0];
        return replay(command, keys.findResourceId(tenant, key).orElseThrow(() -> new IllegalStateException("registered key has no resource")),
                fingerprint);
    }

    private List<PaymentParameter> replay(ChangePaymentParametersCommand command, UUID stored, UUID fingerprint) {
        if (!stored.equals(fingerprint)) throw new BusinessRuleViolation("the Idempotency-Key was already used with another body");
        return parameters.read(command.actor());
    }
}
