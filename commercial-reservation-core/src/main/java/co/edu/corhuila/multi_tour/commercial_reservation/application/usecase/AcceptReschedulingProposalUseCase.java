package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AnswerProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.AnswerReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineResumer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationRescheduled;
import java.time.Clock;
import java.time.Instant;

// HU-COM-018 RN-01, RN-05; AC-18.1, AC-18.6, AC-18.14; plan D-04, D-05: accepting a proposal. The role and the ownership come before anything is written (the customer answers only their own
// proposal; the Administrator and the Collaborator record an outside answer). The same reservation takes the proposed departure with its payments and history, the hold becomes its seat, the frozen
// economic deadlines resume capped by the new start, and the record, the event and the episode commit together. The rejection and the lapses are the next slice.
public class AcceptReschedulingProposalUseCase implements AnswerReschedulingProposal {

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final SalidaCapacityHolder holder;
    private final EconomicDeadlineResumer resumer;
    private final ReschedulingRecords records;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final IdGenerator ids;
    private final Clock clock;

    public AcceptReschedulingProposalUseCase(ReschedulingRepository episodes, ReservationRepository reservations, DepartureReader departures, SalidaCapacityHolder holder,
            EconomicDeadlineResumer resumer, ReschedulingRecords records, PaymentEventOutbox outbox, PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.departures = departures;
        this.holder = holder;
        this.resumer = resumer;
        this.records = records;
        this.outbox = outbox;
        this.transaction = transaction;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ReschedulingEpisode accept(AnswerProposalCommand command) {
        if (command.role() != Role.FINAL_CUSTOMER) PaymentAuthorization.requireFollowUp(new PaymentActor(command.tenantId(), command.actorId(), command.role()));
        if (!command.accepted()) throw new UnsupportedOperationException("the rejection is handled by the next slice");
        Instant now = clock.instant();
        return transaction.inTransaction(() -> {
            ReschedulingEpisode episode = episodes.findForUpdate(command.tenantId(), command.episodeId()).orElseThrow(() -> new ReschedulingNotFoundException(command.episodeId()));
            Reservation reservation = reservations.findByTenantAndId(command.tenantId(), episode.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(episode.reservationId()));
            if (command.role() == Role.FINAL_CUSTOMER && !reservation.customerId().equals(command.actorCustomerId())) throw new PaymentAccessDeniedException();
            ReschedulingEpisode closed = episode.answer(command.toAnswer(), now);
            ReschedulingProposal accepted = closed.proposals().stream().filter(p -> p.state() == ProposalState.ACCEPTED).findFirst().orElseThrow();
            Instant newStart = departures.find(command.tenantId(), accepted.salidaId()).orElseThrow(() -> new DepartureNotFoundException(accepted.salidaId())).startsAt();
            holder.acceptReschedulingHold(command.tenantId(), reservation.reservationId(), accepted.salidaId());
            reservation.rescheduledTo(accepted.salidaId());
            reservations.save(reservation);
            resumer.resume(command.tenantId(), reservation.reservationId(), now, newStart);
            ReschedulingRecord record = new ReschedulingRecord(ids.newId(), command.tenantId(), episode.id(), reservation.reservationId(), null, accepted.salidaId(), newStart,
                    accepted.answer().recordedBy(), now);
            records.save(record);
            outbox.append(new ReservationRescheduled(command.tenantId(), reservation.reservationId(), record.id(), null, episode.originalSalidaId(), accepted.salidaId(), newStart,
                    record.approvedBy(), now));
            episodes.save(closed);
            return closed;
        });
    }
}
