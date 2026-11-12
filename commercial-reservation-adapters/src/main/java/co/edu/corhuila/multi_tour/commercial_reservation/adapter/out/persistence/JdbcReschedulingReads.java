package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018 AC-18.16, AC-18.18: the reads of the episodes over the tables of the db. The tenant leads every query and the reservation is joined for its customer, so a customer that is
// not the holder finds nothing. The page names the episodes and the repository loads each one with its proposals, so the shape of an episode is read in one place.
public final class JdbcReschedulingReads implements ReschedulingReads {
    private static final String FROM = " FROM commercial_reservation.rescheduling_episode e JOIN commercial_reservation.reservation r ON r.tenant_id = e.tenant_id "
            + "AND r.reservation_id = e.reservation_id WHERE e.tenant_id = ?";
    private final JdbcTemplate jdbc;
    private final ReschedulingRepository episodes;

    public JdbcReschedulingReads(JdbcTemplate jdbc, ReschedulingRepository episodes) {
        this.jdbc = jdbc;
        this.episodes = episodes;
    }

    @Override
    public RefundPage<ReschedulingView> page(UUID tenantId, UUID customerId, UUID reservationId, UUID departureId, EpisodeState state, int page, int limit) {
        List<Object> args = new ArrayList<>(List.of(tenantId));
        StringBuilder where = new StringBuilder();
        if (customerId != null) { where.append(" AND r.customer_id = ?"); args.add(customerId); }
        if (reservationId != null) { where.append(" AND e.reservation_id = ?"); args.add(reservationId); }
        if (departureId != null) { where.append(" AND e.original_salida_id = ?"); args.add(departureId); }
        if (state != null) { where.append(" AND e.episode_state = ?"); args.add(state.name()); }
        Long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
        args.add(limit);
        args.add((long) (page - 1) * limit);
        List<ReschedulingView> items = jdbc.query("SELECT e.rescheduling_episode_id, r.customer_id" + FROM + where
                + " ORDER BY e.episode_started_at DESC, e.rescheduling_episode_id DESC LIMIT ? OFFSET ?", (row, index) -> view(tenantId, row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
                args.toArray()).stream().flatMap(Optional::stream).toList();
        return new RefundPage<>(items, total == null ? 0 : total);
    }

    @Override
    public Optional<ReschedulingView> find(UUID tenantId, UUID customerId, UUID episodeId) {
        List<Object> args = new ArrayList<>(List.of(tenantId, episodeId));
        String own = "";
        if (customerId != null) { own = " AND r.customer_id = ?"; args.add(customerId); }
        return jdbc.query("SELECT r.customer_id" + FROM + " AND e.rescheduling_episode_id = ?" + own, (row, index) -> row.getObject(1, UUID.class), args.toArray()).stream().findFirst()
                .flatMap(holder -> view(tenantId, episodeId, holder));
    }

    private Optional<ReschedulingView> view(UUID tenantId, UUID episodeId, UUID customerId) {
        return episodes.find(tenantId, episodeId).map(episode -> new ReschedulingView(episode, customerId));
    }
}
