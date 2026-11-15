package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.instant;
import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ClosingReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingDeadlines;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingProposal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018 plan D-02, D-03 of the db (-db 035, 036, guards 011, 012): the episode and its proposals. A row only moves forward and the guards of the engine refuse the rest, so saving writes
// what is new (insert, nothing when the row itself exists; any other unique key of the engine still refuses) and then the forward move of a row that is still open or pending; a closed row is never written again, which makes a repeat of a save a no-op.
// The start of the current proposal period is derived from the stored due instant (24 hours before it). The freeze pair and the accumulated frozen total of the episode, and the remaining minutes of a
// pending proposal, are read and written here (HU-COM-020, AC-18.13): a move of a frozen episode first writes its resumed clocks, then closes it, so no freeze is left on a closed row. Every
// query leads with the tenant, so an episode of another tenant looks missing.
public final class JdbcReschedulingRepository implements ReschedulingRepository {
    private static final String EPISODE = "SELECT rescheduling_episode_id, tenant_id, reservation_id, original_salida_id, episode_started_at, proposal_due_at, episode_state, "
            + "closed_at, closed_reason, opened_at, frozen_at, remaining_episode_minutes, frozen_total_minutes FROM commercial_reservation.rescheduling_episode WHERE tenant_id = ? AND ";
    private final JdbcTemplate jdbc;

    public JdbcReschedulingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) {
        return one("reservation_id = ? AND original_salida_id = ?", tenantId, reservationId, originalSalidaId);
    }

    @Override
    public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) {
        return one("rescheduling_episode_id = ?", tenantId, episodeId);
    }


    // HU-COM-018 AC-18.20: the same read with the episode row locked until the transaction ends, so the transitions over one episode go one after the other and the loser reads it closed.
    @Override
    public Optional<ReschedulingEpisode> findForUpdate(UUID tenantId, UUID episodeId) {
        return jdbc.query(EPISODE + "rescheduling_episode_id = ? FOR UPDATE", (row, index) -> row(row), tenantId, episodeId).stream().findFirst().map(this::withProposals);
    }

    // HU-COM-020 AC-18.13: the open episodes of a tenant, read whole, for the freeze and the resume of the inactivation.
    public List<ReschedulingEpisode> openOf(UUID tenantId) {
        return jdbc.query(EPISODE + "episode_state = 'OPEN' ORDER BY rescheduling_episode_id", (row, index) -> row(row), tenantId).stream().map(this::withProposals).toList();
    }

    // HU-COM-018 AC-18.12: the open episodes of the tenant whose pending proposal is for the cancelled departure, found by the partial index of the pending proposals.
    @Override
    public List<ReschedulingEpisode> findPendingOn(UUID tenantId, UUID proposedSalidaId) {
        return jdbc.query(EPISODE + "rescheduling_episode_id IN (SELECT rescheduling_episode_id FROM commercial_reservation.rescheduling_proposal WHERE tenant_id = ? AND proposed_salida_id = ? AND proposal_state = 'PENDING') "
                + "AND episode_state = 'OPEN' ORDER BY rescheduling_episode_id FOR UPDATE", (row, index) -> row(row), tenantId, tenantId, proposedSalidaId).stream().map(this::withProposals).toList();
    }

    @Override
    public void save(ReschedulingEpisode e) {
        jdbc.update("INSERT INTO commercial_reservation.rescheduling_episode (rescheduling_episode_id, tenant_id, reservation_id, original_salida_id, episode_started_at, "
                + "proposal_due_at, episode_state, closed_at, closed_reason, opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (rescheduling_episode_id) DO NOTHING",
                e.id(), e.tenantId(), e.reservationId(), e.originalSalidaId(), time(e.cancelledAt()), time(e.proposalPeriodEnd()), e.state().name(), time(e.closedAt()),
                e.reason() == null ? null : e.reason().name(), time(e.openedAt()));
        for (ReschedulingProposal p : e.proposals()) {
            jdbc.update("INSERT INTO commercial_reservation.rescheduling_proposal (rescheduling_proposal_id, tenant_id, rescheduling_episode_id, reservation_id, proposed_salida_id, "
                    + "party_size, proposed_by, proposed_at, response_deadline_at, proposal_state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING') ON CONFLICT (rescheduling_proposal_id) DO NOTHING",
                    p.id(), e.tenantId(), e.id(), e.reservationId(), p.salidaId(), p.partySize(), p.proposedBy(), time(p.proposedAt()), time(p.responseDeadlineAt()));
            jdbc.update("UPDATE commercial_reservation.rescheduling_proposal SET response_deadline_at = ?, remaining_response_minutes = ? WHERE tenant_id = ? AND rescheduling_proposal_id = ? AND proposal_state = 'PENDING' "
                    + "AND (response_deadline_at IS DISTINCT FROM ? OR remaining_response_minutes IS DISTINCT FROM ?)", time(p.responseDeadlineAt()), p.remainingResponseMinutes(), e.tenantId(), p.id(),
                    time(p.responseDeadlineAt()), p.remainingResponseMinutes());
            if (!p.pending()) closeProposal(e, p);
        }
        saveClocks(e);
        if (e.state() != EpisodeState.OPEN)
            jdbc.update("UPDATE commercial_reservation.rescheduling_episode SET episode_state = ?, closed_at = ?, closed_reason = ? WHERE tenant_id = ? "
                    + "AND rescheduling_episode_id = ? AND episode_state = 'OPEN'", e.state().name(), time(e.closedAt()), e.reason().name(), e.tenantId(), e.id());
    }

    // The clocks of a row that is still open in the engine: the due instant of the proposal period, the freeze pair and the total of frozen minutes (null until the first resume; the guard lets it only grow).
    private void saveClocks(ReschedulingEpisode e) {
        Long total = e.frozen().isZero() ? null : e.frozen().toMinutes();
        jdbc.update("UPDATE commercial_reservation.rescheduling_episode SET proposal_due_at = ?, frozen_at = ?, remaining_episode_minutes = ?, frozen_total_minutes = ? WHERE tenant_id = ? "
                + "AND rescheduling_episode_id = ? AND episode_state = 'OPEN' AND (proposal_due_at IS DISTINCT FROM ? OR frozen_at IS DISTINCT FROM ? OR remaining_episode_minutes IS DISTINCT FROM ? "
                + "OR frozen_total_minutes IS DISTINCT FROM ?)", time(e.proposalPeriodEnd()), time(e.frozenAt()), e.remainingEpisodeMinutes(), total, e.tenantId(), e.id(),
                time(e.proposalPeriodEnd()), time(e.frozenAt()), e.remainingEpisodeMinutes(), total);
    }

    private void closeProposal(ReschedulingEpisode e, ReschedulingProposal p) {
        ProposalAnswer a = p.answer();
        jdbc.update("UPDATE commercial_reservation.rescheduling_proposal SET proposal_state = ?, answer_source = ?, answer_channel = ?, answer_evidence = ?, answered_at = ?, "
                + "answer_recorded_by = ?, answer_recorded_at = ?, closed_at = ? WHERE tenant_id = ? AND rescheduling_proposal_id = ? AND proposal_state = 'PENDING'",
                p.state().name(), a == null ? null : a.source().name(), a == null ? null : a.channel(), a == null ? null : a.evidence(), a == null ? null : time(a.answeredAt()),
                a == null ? null : a.recordedBy(), a == null ? null : time(p.closedAt()), time(p.closedAt()), e.tenantId(), p.id());
    }

    private Optional<ReschedulingEpisode> one(String condition, UUID tenantId, Object... keys) {
        Object[] args = new Object[keys.length + 1];
        args[0] = tenantId;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return jdbc.query(EPISODE + condition, (row, index) -> row(row), args).stream().findFirst().map(this::withProposals);
    }

    private record Header(UUID id, UUID tenantId, UUID reservationId, UUID originalSalidaId, Instant startedAt, Instant dueAt, EpisodeState state, Instant closedAt,
            ClosingReason reason, Instant openedAt, Instant frozenAt, Long remainingMinutes, long frozenTotalMinutes) {
    }

    private static Header row(ResultSet r) throws SQLException {
        String reason = r.getString("closed_reason");
        return new Header(r.getObject("rescheduling_episode_id", UUID.class), r.getObject("tenant_id", UUID.class), r.getObject("reservation_id", UUID.class),
                r.getObject("original_salida_id", UUID.class), instant(r, "episode_started_at"), instant(r, "proposal_due_at"), EpisodeState.valueOf(r.getString("episode_state")),
                instant(r, "closed_at"), reason == null ? null : ClosingReason.valueOf(reason), instant(r, "opened_at"),
                instant(r, "frozen_at"), minutes(r, "remaining_episode_minutes"), r.getLong("frozen_total_minutes"));
    }

    private ReschedulingEpisode withProposals(Header h) {
        List<ReschedulingProposal> proposals = jdbc.query("SELECT rescheduling_proposal_id, proposed_salida_id, party_size, proposed_by, proposed_at, response_deadline_at, proposal_state, "
                + "answer_source, answer_channel, answer_evidence, answered_at, answer_recorded_by, closed_at, remaining_response_minutes FROM commercial_reservation.rescheduling_proposal "
                + "WHERE tenant_id = ? AND rescheduling_episode_id = ? ORDER BY proposed_at, rescheduling_proposal_id", (row, index) -> proposal(row), h.tenantId(), h.id());
        Instant periodStart = h.state() == EpisodeState.OPEN ? h.dueAt().minus(ReschedulingDeadlines.PROPOSAL_PERIOD) : h.startedAt();
        if (periodStart.isBefore(h.startedAt())) periodStart = h.startedAt();
        return new ReschedulingEpisode(h.id(), h.tenantId(), h.reservationId(), h.originalSalidaId(), h.startedAt(), h.openedAt(), h.state(), h.reason(), h.closedAt(), periodStart,
                Duration.ofMinutes(h.frozenTotalMinutes()), proposals, h.frozenAt(), h.remainingMinutes());
    }

    private static ReschedulingProposal proposal(ResultSet r) throws SQLException {
        ProposalState state = ProposalState.valueOf(r.getString("proposal_state"));
        String source = r.getString("answer_source");
        ProposalAnswer answer = source == null ? null : new ProposalAnswer(state == ProposalState.ACCEPTED, ProposalAnswer.Source.valueOf(source), r.getString("answer_channel"),
                r.getString("answer_evidence"), instant(r, "answered_at"), r.getObject("answer_recorded_by", UUID.class));
        return new ReschedulingProposal(r.getObject("rescheduling_proposal_id", UUID.class), r.getObject("proposed_salida_id", UUID.class), r.getInt("party_size"),
                r.getObject("proposed_by", UUID.class), instant(r, "proposed_at"), instant(r, "response_deadline_at"), state, instant(r, "closed_at"), answer,
                minutes(r, "remaining_response_minutes"));
    }
    // The column is an integer, which the driver does not hand over as a Long.
    private static Long minutes(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : value;
    }
}
