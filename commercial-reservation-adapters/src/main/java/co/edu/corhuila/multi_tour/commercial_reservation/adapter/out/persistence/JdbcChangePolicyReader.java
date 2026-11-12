package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ChangePolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangePolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ChangeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 plan D-04: the terms of a tour per change type from service_change_policy; no row means no policy and the change is refused
// (DP-01). Nothing in this service writes the table yet (declared debt DD-2).
public final class JdbcChangePolicyReader implements ChangePolicyReader {
    private final JdbcTemplate jdbc;

    public JdbcChangePolicyReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChangePolicy> policyFor(UUID tenantId, UUID serviceId, ChangeType changeType) {
        return jdbc.query("SELECT change_allowed, request_lead_minutes FROM commercial_reservation.service_change_policy WHERE tenant_id = ? "
                + "AND service_id = ? AND change_type = ?", (row, index) -> new ChangePolicy(row.getBoolean("change_allowed"),
                row.getInt("request_lead_minutes")), tenantId, serviceId, changeType.name()).stream().findFirst();
    }
}
