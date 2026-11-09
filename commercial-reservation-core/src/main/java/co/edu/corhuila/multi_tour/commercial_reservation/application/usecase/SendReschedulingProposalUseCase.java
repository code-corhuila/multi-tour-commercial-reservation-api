package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Instant;

// HU-COM-018 RN-02, RN-03; AC-18.3, AC-18.4, AC-18.9; plan D-04: the operator proposes a new departure for an episode. The checks come in the order of the plan (role, proof, service of the
// proposed departure, period still open, seats by the conditional update) and nothing is written before they pass; the seats of every traveller are held in the proposed departure until the
// customer answers, and the reservation keeps its date until then. The proposal and the hold commit together.
public class SendReschedulingProposalUseCase implements SendReschedulingProposal {

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final SalidaCapacityHolder holder;
    private final PaymentTransaction transaction;
    private final IdGenerator ids;
    private final Clock clock;

    public SendReschedulingProposalUseCase(ReschedulingRepository episodes, ReservationRepository reservations, DepartureReader departures, SalidaCapacityHolder holder,
            PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.departures = departures;
        this.holder = holder;
        this.transaction = transaction;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ReschedulingEpisode send(SendProposalCommand command) {
        PaymentAuthorization.requireFollowUp(new PaymentActor(command.tenantId(), command.actorId(), command.role()));
        Instant now = clock.instant();
        return transaction.inTransaction(() -> {
            ReschedulingEpisode episode = episodes.find(command.tenantId(), command.episodeId()).orElseThrow(() -> new ReschedulingNotFoundException(command.episodeId()));
            Reservation reservation = reservations.findByTenantAndId(command.tenantId(), episode.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(episode.reservationId()));
            if (reservation.attempts().stream().anyMatch(a -> a.validationStatus() == ValidationStatus.IN_VALIDATION))
                throw new BusinessRuleViolation("a proof is in validation: it must be resolved before a proposal");
            DepartureState proposed = departures.find(command.tenantId(), command.proposedSalidaId()).orElseThrow(() -> new DepartureNotFoundException(command.proposedSalidaId()));
            if (!proposed.serviceId().equals(reservation.service().serviceId())) throw new BusinessRuleViolation("the proposed departure belongs to another service");
            if (proposed.status() != DepartureStatus.SCHEDULED || !proposed.startsAt().isAfter(now)) throw new BusinessRuleViolation("the proposed departure is not open");
            ReschedulingEpisode next = episode.propose(ids.newId(), proposed.departureId(), reservation.people(), command.actorId(), now, proposed.startsAt());
            if (!holder.holdForRescheduling(command.tenantId(), reservation.reservationId(), proposed.departureId(), reservation.people()))
                throw new BusinessRuleViolation("the proposed departure has no seats for every traveller");
            episodes.save(next);
            return next;
        });
    }
}
