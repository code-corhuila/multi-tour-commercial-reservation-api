package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-019 plan D-03 (-db 032, 011, 016): one read by key of the facts that decide the anchor. The departure comes from the local mirror through an outer join, so
// a departure the mirror never saw reads as unknown facts. A cancelled reservation has nothing to treat when no financial decision was ever opened for it (DP: that is
// the only trace of a value to treat that this domain keeps). Nothing sensitive is read; every query leads with the tenant.
public final class JdbcRetentionFactsReader implements RetentionFactsReader {
    // PDR section 9, G14: the one tenant of Phase 1 lives in this zone; a tenant with no copy is treated with it (plan D-07).
    private static final String DEFAULT_ZONE = "America/Bogota";
    private final JdbcTemplate jdbc;

    public JdbcRetentionFactsReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // A year of the tenant zone is never more than a day away from a year of UTC, so the pre-filter reads one day short and the pure function decides.
    @Override
    public List<UUID> dueCandidates(UUID tenantId, Instant now, int limit, UUID after) {
        var threshold = now.minusSeconds(364L * 86_400).atOffset(ZoneOffset.UTC);
        return jdbc.queryForList("SELECT r.reservation_id FROM commercial_reservation.reservation r LEFT JOIN commercial_reservation.salida_capacity s ON s.tenant_id = r.tenant_id "
                + "AND s.salida_id = r.salida_id WHERE r.tenant_id = ? AND (?::uuid IS NULL OR r.reservation_id > ?::uuid) AND NOT EXISTS (SELECT 1 FROM "
                + "commercial_reservation.sensitive_data_deletion d WHERE d.reservation_id = r.reservation_id) AND (s.finalized_at <= ? OR s.estimated_ends_at <= ? "
                + "OR r.cancelled_at <= ?) ORDER BY r.reservation_id LIMIT ?", UUID.class, tenantId, after, after, threshold, threshold, threshold, limit);
    }

    @Override
    public Optional<RetentionFacts> factsOf(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT r.customer_id, t.time_zone, r.reservation_status, r.cancelled_at, s.status AS departure_status, s.estimated_ends_at, s.finalized_at, "
                + "NOT EXISTS (SELECT 1 FROM commercial_reservation.financial_decision d WHERE d.tenant_id = r.tenant_id AND d.reservation_id = r.reservation_id) "
                + "AS nothing_to_treat FROM commercial_reservation.reservation r LEFT JOIN commercial_reservation.salida_capacity s ON s.tenant_id = r.tenant_id "
                + "AND s.salida_id = r.salida_id LEFT JOIN commercial_reservation.tenant_status t ON t.tenant_id = r.tenant_id WHERE r.tenant_id = ? AND r.reservation_id = ?", (row, index) -> {
                    String departure = row.getString("departure_status");
                    String zone = row.getString("time_zone");
                    return new RetentionFacts(row.getObject("customer_id", UUID.class), ZoneId.of(zone == null ? DEFAULT_ZONE : zone),
                            new DepartureFacts(departure == null ? null : DepartureStatus.valueOf(departure), instant(row, "estimated_ends_at"),
                                    instant(row, "finalized_at")),
                            new ReservationFacts(ReservationStatus.valueOf(row.getString("reservation_status")), instant(row, "cancelled_at"),
                                    row.getBoolean("nothing_to_treat")));
                }, tenantId, reservationId).stream().findFirst();
    }
}
