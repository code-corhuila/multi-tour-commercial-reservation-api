package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The minimum deposit of a reservation in cents: the basis points of the DEPOSIT row of the service (else the row of the tenant) over
// the final value after discounts, rounded up to the cent (010 DP-01). Empty when the tenant has no such row.
public final class JdbcPaymentConditionReader implements PaymentConditionReader {
    private final JdbcTemplate jdbc;

    public JdbcPaymentConditionReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cents> minimumDepositFor(UUID tenantId, Reservation reservation) {
        return jdbc.query("SELECT minimum_deposit_bp FROM commercial_reservation.payment_condition WHERE tenant_id = ? "
                + "AND payment_type = 'DEPOSIT' AND (service_id = ? OR service_id IS NULL) ORDER BY service_id IS NULL LIMIT 1",
                (row, index) -> row.getInt("minimum_deposit_bp"), tenantId, reservation.service().serviceId()).stream().findFirst()
                .map(bp -> new Cents((reservation.finalValue().amount() * bp + 9_999L) / 10_000L));
    }
}
