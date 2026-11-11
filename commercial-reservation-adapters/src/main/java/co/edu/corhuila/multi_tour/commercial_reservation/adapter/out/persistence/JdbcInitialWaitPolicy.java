package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.InitialWaitPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// initial_wait_hours of the payment condition of the reservation type: the row of the service before the row of the tenant.
public final class JdbcInitialWaitPolicy implements InitialWaitPolicy {
    private final JdbcTemplate jdbc;

    public JdbcInitialWaitPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> initialWaitHours(UUID tenantId, Reservation reservation) {
        return jdbc.query("SELECT initial_wait_hours FROM commercial_reservation.payment_condition WHERE tenant_id = ? AND payment_type = ? "
                + "AND (service_id = ? OR service_id IS NULL) ORDER BY service_id IS NULL LIMIT 1", (row, index) -> row.getInt("initial_wait_hours"),
                tenantId, reservation.expectedPaymentType().name(), reservation.service().serviceId()).stream().findFirst();
    }
}
