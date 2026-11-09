package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OpenReschedulingCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DeadlineFreezeCause;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineFreezer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Clock;
import java.time.Instant;

// HU-COM-018 RN-07, AC-18.8; plan D-06: the saga opens the episode of one reservation when its departure is cancelled or interrupted for a cause not attributable to the
// customer. One episode per reservation and incident (a repeat returns the stored one and changes nothing); the mark, the release of the cancelled seat and the freeze of the
// economic deadlines commit together; the 24 hours count from the cancellation instant the saga sends. Called by the internal route, never by a person.
public class OpenReschedulingUseCase implements OpenRescheduling {

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final SalidaCapacityHolder holder;
    private final EconomicDeadlineFreezer freezer;
    private final PaymentTransaction transaction;
    private final IdGenerator ids;
    private final Clock clock;

    public OpenReschedulingUseCase(ReschedulingRepository episodes, ReservationRepository reservations, SalidaCapacityHolder holder, EconomicDeadlineFreezer freezer,
            PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.holder = holder;
        this.freezer = freezer;
        this.transaction = transaction;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ReschedulingEpisode open(OpenReschedulingCommand command) {
        Instant now = clock.instant();
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.findByTenantAndId(command.tenantId(), command.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));
            return episodes.findByIncident(command.tenantId(), command.reservationId(), command.departureId()).orElseGet(() -> {
                if (!reservation.departureId().equals(command.departureId())) throw new BusinessRuleViolation("the reservation is not on the cancelled departure");
                if (command.cancelledAt().isAfter(now)) throw new BusinessRuleViolation("the cancellation cannot be in the future");
                ReschedulingEpisode episode = ReschedulingEpisode.open(ids.newId(), command.tenantId(), command.reservationId(), command.departureId(), command.cancelledAt(), now);
                episodes.save(episode);
                if (reservation.seatHeld()) {
                    holder.release(command.tenantId(), command.reservationId());
                    reservation.releaseSeat("DEPARTURE_CANCELLED_OR_INTERRUPTED", now);
                    reservations.save(reservation);
                }
                freezer.freeze(command.tenantId(), command.reservationId(), now, DeadlineFreezeCause.DEPARTURE_CANCELLED_OR_INTERRUPTED);
                return episode;
            });
        });
    }
}
