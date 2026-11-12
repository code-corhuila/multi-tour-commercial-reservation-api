package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DueRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingDeadlines;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018 AC-18.10, AC-18.11: the due list of job 5 across tenants, ordered by the instant the first clock ran out, read with the partial indexes of the db (open episodes by due instant, pending
// proposals by response deadline). A frozen episode is not due. The 96-hour limit is judged from the episode start; the service computes it again at the expiry.
public final class JdbcReschedulingQueries implements ReschedulingQueries {
    private final JdbcTemplate jdbc;

    public JdbcReschedulingQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DueRescheduling> due(Instant now, int limit) {
        return jdbc.query("SELECT e.tenant_id, e.reservation_id, e.rescheduling_episode_id FROM commercial_reservation.rescheduling_episode e WHERE e.episode_state = 'OPEN' AND e.frozen_at IS NULL AND ("
                + "(e.proposal_due_at <= ? AND NOT EXISTS (SELECT 1 FROM commercial_reservation.rescheduling_proposal p WHERE p.tenant_id = e.tenant_id AND p.rescheduling_episode_id = e.rescheduling_episode_id AND p.proposal_state = 'PENDING')) "
                + "OR EXISTS (SELECT 1 FROM commercial_reservation.rescheduling_proposal p WHERE p.tenant_id = e.tenant_id AND p.rescheduling_episode_id = e.rescheduling_episode_id AND p.proposal_state = 'PENDING' AND p.response_deadline_at <= ?) "
                + "OR e.episode_started_at + COALESCE(e.frozen_total_minutes, 0) * interval '1 minute' <= ?) ORDER BY e.proposal_due_at, e.rescheduling_episode_id LIMIT ?",
                (row, index) -> new DueRescheduling(row.getObject("tenant_id", java.util.UUID.class), row.getObject("reservation_id", java.util.UUID.class), row.getObject("rescheduling_episode_id", java.util.UUID.class)),
                time(now), time(now), time(now.minus(ReschedulingDeadlines.EPISODE_LIMIT)), limit);
    }
}
