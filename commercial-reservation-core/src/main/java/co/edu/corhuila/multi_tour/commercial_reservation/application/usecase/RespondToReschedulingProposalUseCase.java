package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.AnswerReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RespondToReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// HU-COM-018 AC-18.6, AC-18.15, AC-18.18, plan D-07: the role comes before any load. A Final Customer is resolved from their account to their customer record and must be the customer of the
// reservation of the episode, or the episode looks missing (never forbidden); a proposal that is not one of the episode looks missing too. The answer is built here, so a channel, an evidence
// or an instant that is not valid is refused before anything is read. Then the episode decides: ACCEPT is the acceptance, REJECT the cancellation with a full refund.
public class RespondToReschedulingProposalUseCase implements RespondToReschedulingProposal {
    private static final Set<Role> STAFF = Set.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST);

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final CustomerByAccount accounts;
    private final AnswerReschedulingProposal acceptance;
    private final ConcludeRescheduling conclusion;

    public RespondToReschedulingProposalUseCase(ReschedulingRepository episodes, ReservationRepository reservations, CustomerByAccount accounts, AnswerReschedulingProposal acceptance,
            ConcludeRescheduling conclusion) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.accounts = accounts;
        this.acceptance = acceptance;
        this.conclusion = conclusion;
    }

    @Override
    public ReschedulingEpisode answerAsCustomer(PaymentActor actor, UUID episodeId, UUID proposalId, boolean accepted) {
        if (actor == null || actor.role() != Role.FINAL_CUSTOMER) throw new PaymentAccessDeniedException();
        UUID customer = accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId()).map(Customer::customerId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
        requireProposal(requireOwned(actor, customer, episodeId), episodeId, proposalId);
        return route(AnswerProposalCommand.customer(actor.tenantId(), actor.actorId(), customer, episodeId, accepted));
    }

    @Override
    public ReschedulingEpisode recordOutside(PaymentActor actor, UUID episodeId, UUID proposalId, boolean accepted, String channel, String evidence, Instant answeredAt) {
        PaymentAuthorization.requireFollowUp(actor);
        AnswerProposalCommand command = AnswerProposalCommand.outside(actor.tenantId(), actor.actorId(), actor.role(), episodeId, accepted, channel, evidence, answeredAt);
        command.toAnswer();
        requireProposal(requireOwned(actor, null, episodeId), episodeId, proposalId);
        return route(command);
    }

    @Override
    public ReschedulingEpisode visibleTo(PaymentActor actor, UUID episodeId) {
        if (actor == null || actor.tenantId() == null) throw new PaymentAccessDeniedException();
        if (actor.role() == Role.FINAL_CUSTOMER) {
            UUID customer = accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId()).map(Customer::customerId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
            return requireOwned(actor, customer, episodeId);
        }
        if (!STAFF.contains(actor.role())) throw new PaymentAccessDeniedException();
        return requireOwned(actor, null, episodeId);
    }

    private ReschedulingEpisode requireOwned(PaymentActor actor, UUID customer, UUID episodeId) {
        ReschedulingEpisode episode = episodes.find(actor.tenantId(), episodeId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
        if (customer != null && !reservations.findByTenantAndId(actor.tenantId(), episode.reservationId()).map(r -> customer.equals(r.customerId())).orElse(false))
            throw new ReschedulingNotFoundException(episodeId);
        return episode;
    }

    private static void requireProposal(ReschedulingEpisode episode, UUID episodeId, UUID proposalId) {
        if (episode.proposals().stream().noneMatch(p -> p.id().equals(proposalId))) throw new ReschedulingNotFoundException(episodeId);
    }

    private ReschedulingEpisode route(AnswerProposalCommand command) {
        return command.accepted() ? acceptance.accept(command) : conclusion.reject(command);
    }
}
