package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The tenant, the tour and the departure are conditions of every statement, so a departure of another tenant is the same empty answer as a missing one. The rows
// come from the reservation and the name of its lead customer; no other personal field is read.
public final class JdbcDepartureReservationQueries implements DepartureReservationQueries {
    private final JdbcTemplate jdbc;

    public JdbcDepartureReservationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DepartureReservationPage> ofDeparture(UUID tenantId, UUID serviceId, UUID salidaId, int page, int limit) {
        int known = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ? AND service_id = ?",
                Integer.class, tenantId, salidaId, serviceId);
        if (known == 0) return Optional.empty();
        long total = jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation WHERE tenant_id = ? AND salida_id = ? AND service_id = ?",
                Long.class, tenantId, salidaId, serviceId);
        List<DepartureReservation> rows = jdbc.query("SELECT r.reservation_id, c.full_name, r.party_size, r.reservation_status FROM commercial_reservation.reservation r "
                + "JOIN commercial_reservation.customer c ON c.tenant_id = r.tenant_id AND c.customer_id = r.customer_id "
                + "WHERE r.tenant_id = ? AND r.salida_id = ? AND r.service_id = ? ORDER BY r.created_at, r.reservation_id LIMIT ? OFFSET ?",
                (row, index) -> new DepartureReservation(row.getObject("reservation_id", UUID.class), row.getString("full_name"), row.getInt("party_size"),
                        ReservationStatus.valueOf(row.getString("reservation_status"))), tenantId, salidaId, serviceId, limit, (page - 1L) * limit);
        return Optional.of(new DepartureReservationPage(rows, total));
    }
}
