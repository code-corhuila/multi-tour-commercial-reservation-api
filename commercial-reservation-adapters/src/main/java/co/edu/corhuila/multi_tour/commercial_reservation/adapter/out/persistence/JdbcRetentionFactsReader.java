package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019 plan D-03 (-db 032, 011, 016): one read by key of the facts that decide the anchor. The departure comes from the local mirror through an outer join, so
// a departure the mirror never saw reads as unknown facts. A cancelled reservation has nothing to treat when no financial decision was ever opened for it (DP: that is
// the only trace of a value to treat that this domain keeps). Nothing sensitive is read; every query leads with the tenant.
public final class JdbcRetentionFactsReader implements RetentionFactsReader {
    private final JdbcTemplate jdbc;

    public JdbcRetentionFactsReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RetentionFacts> factsOf(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT r.customer_id, r.reservation_status, r.cancelled_at, s.status AS departure_status, s.estimated_ends_at, s.finalized_at, "
                + "NOT EXISTS (SELECT 1 FROM commercial_reservation.financial_decision d WHERE d.tenant_id = r.tenant_id AND d.reservation_id = r.reservation_id) "
                + "AS nothing_to_treat FROM commercial_reservation.reservation r LEFT JOIN commercial_reservation.salida_capacity s ON s.tenant_id = r.tenant_id "
                + "AND s.salida_id = r.salida_id WHERE r.tenant_id = ? AND r.reservation_id = ?", (row, index) -> {
                    String departure = row.getString("departure_status");
                    return new RetentionFacts(row.getObject("customer_id", UUID.class),
                            new DepartureFacts(departure == null ? null : DepartureStatus.valueOf(departure), instant(row, "estimated_ends_at"),
                                    instant(row, "finalized_at")),
                            new ReservationFacts(ReservationStatus.valueOf(row.getString("reservation_status")), instant(row, "cancelled_at"),
                                    row.getBoolean("nothing_to_treat")));
                }, tenantId, reservationId).stream().findFirst();
    }
}
