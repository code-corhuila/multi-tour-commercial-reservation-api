package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// DP-05: additional_wait_hours of the payment condition of the reservation type, the row of the service before the row of the tenant.
// Empty when the tenant has none: no number is invented.
public final class JdbcPaymentExtensionPolicy implements PaymentExtensionPolicy {
    private final JdbcTemplate jdbc;

    public JdbcPaymentExtensionPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> maximumHours(UUID tenantId, Reservation reservation) {
        return jdbc.query("SELECT additional_wait_hours FROM commercial_reservation.payment_condition WHERE tenant_id = ? AND payment_type = ? "
                + "AND (service_id = ? OR service_id IS NULL) ORDER BY service_id IS NULL LIMIT 1", (row, index) -> row.getInt("additional_wait_hours"),
                tenantId, reservation.expectedPaymentType().name(), reservation.service().serviceId()).stream().findFirst();
    }
}
