package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import java.util.Optional;
import java.util.UUID;

// HU-COM-012 DP-01 (PROPOSED DEFAULT, spec 012 block of 2026-11-12): a tour with no stored terms for MODIFICATION uses the only configuration
// example the PDR v1.9 confirms (RN-RES-004: "cambio de tour hasta 6 horas antes"): allowed, 360 minutes before the departure start. DATE_CHANGE
// has no default and stays refused (GAP-07). A stored row, allowing or forbidding, always prevails; reading never writes a row.
public final class DefaultingChangePolicyReader implements ChangePolicyReader {
    static final ChangePolicy MODIFICATION_DEFAULT = new ChangePolicy(true, 6 * 60);

    private final ChangePolicyReader stored;

    public DefaultingChangePolicyReader(ChangePolicyReader stored) {
        this.stored = stored;
    }

    @Override
    public Optional<ChangePolicy> policyFor(UUID tenantId, UUID serviceId, ChangeType changeType) {
        Optional<ChangePolicy> row = stored.policyFor(tenantId, serviceId, changeType);
        if (row.isPresent() || changeType != ChangeType.MODIFICATION) return row;
        return Optional.of(MODIFICATION_DEFAULT);
    }
}
