package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingProposal;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Rescheduling of the contract (PROPOSED, AC-18.5, AC-18.16): the episode with the deadline in force, its proposals and its history. The deadlines are the ones the aggregate computes, never
// a stored copy. The evidence of an outside answer is never part of the answer (AC-18.19, the history has no evidence text); the channel is. frozen is false until the freeze of the
// tenant (HU-COM-020) has a caller: the adapter of the episodes does not read the freeze pair yet. heldSeats is the party size while the proposal is pending and zero after.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReschedulingResponse(UUID reschedulingId, UUID reservationId, UUID customerId, UUID originalDepartureId, String state, String closingReason, String cancelledAt,
        String proposalDeadlineAt, String episodeLimitAt, boolean frozen, List<Proposal> proposals, List<History> history) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Proposal(UUID proposalId, UUID proposedDepartureId, String state, String notifiedAt, String responseDeadlineAt, int heldSeats, String answeredAt, UUID answeredBy,
            String answerSource, String channel, String recordedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record History(String at, UUID actor, String action, String detail) {
    }

    public static ReschedulingResponse of(ReschedulingEpisode episode) {
        return of(new ReschedulingView(episode, null));
    }

    static ReschedulingResponse of(ReschedulingView view) {
        ReschedulingEpisode e = view.episode();
        return new ReschedulingResponse(e.id(), e.reservationId(), view.customerId(), e.originalSalidaId(), e.state().name(), e.reason() == null ? null : e.reason().name(),
                e.cancelledAt().toString(), e.proposalPeriodEnd().toString(), e.episodeLimit().toString(), false, e.proposals().stream().map(ReschedulingResponse::proposal).toList(),
                history(e));
    }

    private static Proposal proposal(ReschedulingProposal p) {
        ProposalAnswer a = p.answer();
        return new Proposal(p.id(), p.salidaId(), p.state().name(), p.proposedAt().toString(), p.responseDeadlineAt().toString(), p.pending() ? p.partySize() : 0,
                a == null ? null : a.answeredAt().toString(), a == null ? null : a.recordedBy(),
                a == null ? null : a.source() == ProposalAnswer.Source.CUSTOMER ? "CUSTOMER" : "EXTERNAL", a == null ? null : a.channel(), a == null ? null : p.closedAt().toString());
    }

    // DP-12: derived from the rows of the episode and its proposals; the detail is a code, never free text.
    private static List<History> history(ReschedulingEpisode e) {
        List<History> entries = new ArrayList<>();
        entries.add(new History(e.openedAt().toString(), null, "EPISODE_OPENED", null));
        for (ReschedulingProposal p : e.proposals()) {
            entries.add(new History(p.proposedAt().toString(), p.proposedBy(), "PROPOSAL_SENT", null));
            if (p.state() == ProposalState.PENDING) continue;
            ProposalAnswer a = p.answer();
            entries.add(new History(p.closedAt().toString(), a == null ? null : a.recordedBy(), "PROPOSAL_" + p.state().name(),
                    a == null ? null : a.source().name() + (a.channel() == null ? "" : ":" + a.channel())));
        }
        if (e.closedAt() != null) entries.add(new History(e.closedAt().toString(), null, "EPISODE_" + e.state().name(), e.reason().name()));
        entries.sort(Comparator.comparing(entry -> Instant.parse(entry.at())));
        return entries;
    }
}
