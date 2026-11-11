package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingProposal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// HU-COM-018, the Rescheduling schema of the contract (PROPOSED): the episode with the clocks the service computes and the history derived from its rows (DP-12). The evidence text of an
// answer is never part of it. The frozen flag is false until the freeze of an episode exists (HU-COM-020 slice).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReschedulingResponse(UUID reschedulingId, UUID reservationId, UUID originalDepartureId, String state, String closingReason, String cancelledAt, String proposalDeadlineAt,
        String episodeLimitAt, boolean frozen, List<Proposal> proposals, List<History> history) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Proposal(UUID proposalId, UUID proposedDepartureId, String state, String notifiedAt, String responseDeadlineAt, int heldSeats, String answeredAt, UUID answeredBy,
            String answerSource, String channel) {
        static Proposal of(ReschedulingProposal p) {
            ProposalAnswer a = p.answer();
            return new Proposal(p.id(), p.salidaId(), p.state().name(), p.proposedAt().toString(), p.responseDeadlineAt().toString(), p.state() == ProposalState.PENDING ? p.partySize() : 0,
                    a == null ? null : a.answeredAt().toString(), a == null ? null : a.recordedBy(), a == null ? null : a.source() == ProposalAnswer.Source.CUSTOMER ? "CUSTOMER" : "EXTERNAL",
                    a == null ? null : a.channel());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record History(String at, UUID actor, String action, String detail) {}

    public static ReschedulingResponse of(ReschedulingEpisode e) {
        List<History> history = new ArrayList<>();
        history.add(new History(e.openedAt().toString(), null, "EPISODE_OPENED", null));
        for (ReschedulingProposal p : e.proposals()) {
            history.add(new History(p.proposedAt().toString(), p.proposedBy(), "PROPOSAL_SENT", null));
            if (!p.pending()) history.add(new History(p.closedAt().toString(), null, "PROPOSAL_" + p.state().name(), null));
        }
        if (e.closedAt() != null) history.add(new History(e.closedAt().toString(), null, "EPISODE_" + e.state().name(), e.reason().name()));
        history.sort(Comparator.comparing(History::at));
        return new ReschedulingResponse(e.id(), e.reservationId(), e.originalSalidaId(), e.state().name(), e.reason() == null ? null : e.reason().name(), e.cancelledAt().toString(),
                e.proposalPeriodEnd().toString(), e.episodeLimit().toString(), false, e.proposals().stream().map(Proposal::of).toList(), history);
    }
}
