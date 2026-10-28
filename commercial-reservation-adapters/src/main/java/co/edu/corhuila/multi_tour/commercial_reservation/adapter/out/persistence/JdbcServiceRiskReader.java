package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcServiceRiskReader implements ServiceRiskReader {
    private final JdbcTemplate jdbc;

    public JdbcServiceRiskReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Boolean> isHighRisk(UUID tenantId, UUID serviceId) {
        return jdbc.query("SELECT high_risk FROM commercial_reservation.catalog_service "
                + "WHERE tenant_id = ? AND service_id = ?", (row, index) -> row.getBoolean("high_risk"),
                tenantId, serviceId).stream().findFirst();
    }
}
