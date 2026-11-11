package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-007 DP-03: the per-person transport fare is the transport_rate_per_person_cents column of the catalog service of the
// tour. A NULL column (not parametrized), an unknown tour and another tenant's tour are all the same empty answer, so the use
// case still refuses a transport-required tour that has no fare (RN-15). The departure does not change the fare.
public final class JdbcTransportRateReader implements TransportRateReader {
    private final JdbcTemplate jdbc;

    public JdbcTransportRateReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cents> perPersonCents(UUID tenantId, UUID tourId, UUID departureId) {
        return jdbc.query("SELECT transport_rate_per_person_cents FROM commercial_reservation.catalog_service "
                        + "WHERE tenant_id = ? AND service_id = ?",
                (row, index) -> Optional.ofNullable(row.getObject(1, Long.class)).map(Cents::new), tenantId, tourId)
                .stream().findFirst().flatMap(rate -> rate);
    }
}
