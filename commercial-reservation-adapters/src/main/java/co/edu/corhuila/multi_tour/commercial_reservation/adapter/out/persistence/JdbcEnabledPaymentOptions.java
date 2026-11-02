package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// RN-14: the medium must be enabled for the tenant and, when configured, for the service. The condition of the service wins over
// the one of the tenant; with neither (DP-05 left the default open, HU-COM-009 owns the configuration) everything is enabled.
public final class JdbcEnabledPaymentOptions implements EnabledPaymentOptions {
    private final JdbcTemplate jdbc;

    public JdbcEnabledPaymentOptions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isEnabled(UUID tenantId, UUID serviceId, PaymentType type, PaymentMethod method) {
        List<Boolean> found = jdbc.query("SELECT CASE WHEN ? = 'TRANSFER' THEN transfer_enabled ELSE cash_enabled END "
                + "FROM commercial_reservation.payment_condition WHERE tenant_id = ? AND payment_type = ? "
                + "AND (service_id = ? OR service_id IS NULL) ORDER BY service_id IS NULL, changed_at DESC LIMIT 1",
                (row, index) -> row.getBoolean(1), method.name(), tenantId, type.name(), serviceId);
        return found.isEmpty() || found.get(0);
    }
}
