package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import java.util.Optional;
import java.util.UUID;

// Red skeleton: passes through the delegate until the default of DP-01 is written.
public final class DefaultingChangePolicyReader implements ChangePolicyReader {
    private final ChangePolicyReader delegate;

    public DefaultingChangePolicyReader(ChangePolicyReader delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ChangePolicy> policyFor(UUID tenantId, UUID serviceId, ChangeType changeType) {
        return delegate.policyFor(tenantId, serviceId, changeType);
    }
}
