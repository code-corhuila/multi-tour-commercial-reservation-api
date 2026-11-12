package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEpisodeStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-020 AC-18.13, AC-20.2, AC-20.3 over rescheduling_episode and rescheduling_proposal (freeze pair of HU-COM-018, frozen_total_minutes of this story): the open episodes of one tenant, read whole through
// the repository mapping, and the write of a frozen or resumed one through its save (the guards of the engine let an open row change only its clocks). Every query leads with the tenant.
public final class JdbcTenantEpisodes implements TenantEpisodeStore {
    private final JdbcReschedulingRepository episodes;

    public JdbcTenantEpisodes(JdbcTemplate jdbc) {
        this.episodes = new JdbcReschedulingRepository(jdbc);
    }

    @Override
    public List<ReschedulingEpisode> openOf(UUID tenantId) {
        return episodes.openOf(tenantId);
    }

    @Override
    public void save(ReschedulingEpisode episode) {
        episodes.save(episode);
    }
}
