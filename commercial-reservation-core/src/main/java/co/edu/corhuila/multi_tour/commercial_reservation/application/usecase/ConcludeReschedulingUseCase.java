package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ClosingReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

// HU-COM-018 RN-06, RN-09; AC-18.7, AC-18.10, AC-18.11; plan D-05: not accepted means cancelled. The rejection, the declaration of no alternative and the expiry close the episode and cancel the
// reservation through its own cancellation with the cause NOT_ATTRIBUTABLE_TO_CUSTOMER; the seats of the pending proposal and of the reservation are released; a refund determination of 100 % of
// the validated values (origin NOT_ATTRIBUTABLE_TO_CUSTOMER, no penalty) is left only when something was paid and validated. All of it commits with the episode.
public class ConcludeReschedulingUseCase implements ConcludeRescheduling {

    private static final java.util.Set<ClosingReason> EXPIRY_REASONS = java.util.Set.of(ClosingReason.PROPOSAL_PERIOD_EXPIRED, ClosingReason.RESPONSE_DEADLINE_EXPIRED, ClosingReason.EPISODE_LIMIT_REACHED);

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final SalidaCapacityHolder holder;
    private final DetermineRefund refunds;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ConcludeReschedulingUseCase(ReschedulingRepository episodes, ReservationRepository reservations, SalidaCapacityHolder holder, DetermineRefund refunds, PaymentEventOutbox outbox,
            PaymentTransaction transaction, Clock clock) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.holder = holder;
        this.refunds = refunds;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public ReschedulingEpisode reject(AnswerProposalCommand command) {
        if (command.role() != Role.FINAL_CUSTOMER) PaymentAuthorization.requireFollowUp(new PaymentActor(command.tenantId(), command.actorId(), command.role()));
        ProposalAnswer answer = command.toAnswer();
        if (answer.accepted()) throw new IllegalArgumentException("the answer is an acceptance");
        return conclude(command.tenantId(), command.episodeId(), command.actorId(), reservation -> {
            if (command.role() == Role.FINAL_CUSTOMER && !reservation.customerId().equals(command.actorCustomerId())) throw new PaymentAccessDeniedException();
            return null;
        }, episode -> false, (episode, now) -> episode.answer(answer, now));
    }

    @Override
    public ReschedulingEpisode declareNoAlternative(UUID tenantId, UUID actorId, Role role, UUID episodeId) {
        PaymentAuthorization.requireFollowUp(new PaymentActor(tenantId, actorId, role));
        return conclude(tenantId, episodeId, actorId, reservation -> null, episode -> episode.reason() == ClosingReason.NO_ALTERNATIVE, (episode, now) -> episode.declareNoAlternative(now));
    }

    @Override
    public ReschedulingEpisode expire(UUID tenantId, UUID episodeId) {
        return conclude(tenantId, episodeId, null, reservation -> null, episode -> episode.state() == EpisodeState.CANCELLED && episode.reason() != null && EXPIRY_REASONS.contains(episode.reason()), (episode, now) -> episode.expire(now));
    }

    private interface Move {
        ReschedulingEpisode apply(ReschedulingEpisode episode, Instant now);
    }

    private ReschedulingEpisode conclude(UUID tenantId, UUID episodeId, UUID by, Function<Reservation, Void> check, Predicate<ReschedulingEpisode> repeated, Move move) {
        Instant now = clock.instant();
        return transaction.inTransaction(() -> {
            ReschedulingEpisode episode = episodes.find(tenantId, episodeId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
            Reservation reservation = reservations.findByTenantAndId(tenantId, episode.reservationId()).orElseThrow(() -> new ReservationNotFoundException(episode.reservationId()));
            if (repeated.test(episode)) return episode;
            check.apply(reservation);
            ReschedulingEpisode closed = move.apply(episode, now);
            if (episode.pendingProposal().isPresent()) holder.releaseReschedulingHold(tenantId, reservation.reservationId());
            int seats = reservation.seatHeld() ? holder.release(tenantId, reservation.reservationId()) : 0;
            if (seats > 0) reservation.releaseSeat(CapacityReleased.DEPARTURE_CANCELLED_OR_INTERRUPTED, now);
            long paid = reservation.finalValue().amount() - reservation.pendingBalance().amount();
            reservation.cancel(CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER, by, now, seats);
            reservations.save(reservation);
            reservation.pullEvents().forEach(outbox::append);
            if (paid > 0) refunds.determine(tenantId, reservation.reservationId(), RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, null);
            episodes.save(closed);
            return closed;
        });
    }
}
