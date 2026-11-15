package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineFreezer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineResumer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018 AC-18.1, AC-18.8 over payment_deadline: the freeze and the resume of the economic deadlines of ONE reservation, with the cause of the departure. It reads the rows with
// JdbcTenantDeadlines, decides with the pure PaymentDeadline.frozen / resumed and writes only the rows that changed, in the transaction of the caller. Every query leads with the tenant.
public final class JdbcPaymentDeadlineFreezes implements EconomicDeadlineFreezer, EconomicDeadlineResumer {
    private final JdbcTenantDeadlines deadlines;

    public JdbcPaymentDeadlineFreezes(JdbcTemplate jdbc) {
        this.deadlines = new JdbcTenantDeadlines(jdbc);
    }

    @Override
    public void freeze(UUID tenantId, UUID reservationId, Instant at, FreezeCause cause) {
        for (TenantDeadline row : deadlines.openOf(tenantId, reservationId)) {
            PaymentDeadline frozen = row.deadline().frozen(at, row.departureStart(), cause);
            if (!frozen.equals(row.deadline())) deadlines.save(row.withDeadline(frozen));
        }
    }

    @Override
    public void resume(UUID tenantId, UUID reservationId, Instant at, Instant departureStart) {
        for (TenantDeadline row : deadlines.openOf(tenantId, reservationId)) {
            PaymentDeadline resumed = row.deadline().resumed(at, departureStart, FreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED, false);
            if (!resumed.equals(row.deadline())) deadlines.save(row.withDeadline(resumed));
        }
    }
}
