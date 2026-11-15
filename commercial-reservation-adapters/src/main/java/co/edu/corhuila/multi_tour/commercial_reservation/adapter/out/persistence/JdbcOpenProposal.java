package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OpenProposalReader;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-014 plan D-05, AC-14.8 (declared debt DD-3, closed here): a rescheduling proposal is open on a reservation while a PENDING proposal of HU-COM-018
// belongs to an OPEN episode of that reservation. The query leads with the tenant, so another tenant's reservation never has one.
public final class JdbcOpenProposal implements OpenProposalReader {
    private final JdbcTemplate jdbc;

    public JdbcOpenProposal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isOpen(UUID tenantId, UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM commercial_reservation.rescheduling_proposal p "
                + "JOIN commercial_reservation.rescheduling_episode e ON e.tenant_id = p.tenant_id AND e.rescheduling_episode_id = p.rescheduling_episode_id "
                + "WHERE p.tenant_id = ? AND p.reservation_id = ? AND p.proposal_state = 'PENDING' AND e.episode_state = 'OPEN')",
                Boolean.class, tenantId, reservationId));
    }
}
