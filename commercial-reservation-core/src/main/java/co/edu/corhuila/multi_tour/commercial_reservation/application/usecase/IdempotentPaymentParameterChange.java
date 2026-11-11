package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ChangePaymentParametersCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManagePaymentParameters;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Same mechanism as the other writes (norm 5.3.8): a known key with the same body answers and writes nothing; the same key with
// another body is a rule violation. The key store keeps one id per key, so the id is derived from the body. LIMIT: the repetition
// returns the rows in force now, not a copy of the first answer, so a change made in between shows in it.
public final class IdempotentPaymentParameterChange {
    static final String RESOURCE = "payment-parameters";
    private final ManagePaymentParameters parameters;
    private final IdempotentWrites writes;

    public IdempotentPaymentParameterChange(ManagePaymentParameters parameters, IdempotentWrites writes) {
        this.parameters = parameters;
        this.writes = writes;
    }

    public List<PaymentParameter> change(ChangePaymentParametersCommand command, String key) {
        UUID fingerprint = UUID.nameUUIDFromBytes((command.reason() + "|" + command.parameters()).getBytes(StandardCharsets.UTF_8));
        return writes.run(command.actor().tenantId(), key, RESOURCE, () -> parameters.change(command), changed -> fingerprint,
                stored -> replay(command, stored, fingerprint)).value();
    }

    private List<PaymentParameter> replay(ChangePaymentParametersCommand command, UUID stored, UUID fingerprint) {
        if (!stored.equals(fingerprint)) throw new BusinessRuleViolation("the Idempotency-Key was already used with another body");
        return parameters.read(command.actor());
    }
}
