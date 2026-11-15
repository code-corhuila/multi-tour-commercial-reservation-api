package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// HU-COM-018 RN-08, RN-09; AC-18.12, AC-18.13; plan D-06: the proposed departure was cancelled. Every pending proposal for it is void (not a rejection) and its seats go back at once. With the tenant
// active a new period of the lowest of 24 hours and the episode remainder opens from the cancellation and the reservation stays; with the tenant inactive (I1) the episode and the reservation are
// cancelled at once with a full refund of the validated values and no new period. Called by the saga through the internal route; a repeat finds nothing pending and changes nothing.
public class VoidReschedulingProposalsUseCase implements VoidReschedulingProposals {

    private final ReschedulingRepository episodes;
    private final ReservationRepository reservations;
    private final SalidaCapacityHolder holder;
    private final ReschedulingCancellation cancellation;
    private final TenantStatusReader tenants;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public VoidReschedulingProposalsUseCase(ReschedulingRepository episodes, ReservationRepository reservations, SalidaCapacityHolder holder, DetermineRefund refunds,
            PaymentEventOutbox outbox, TenantStatusReader tenants, PaymentTransaction transaction, Clock clock) {
        this.episodes = episodes;
        this.reservations = reservations;
        this.holder = holder;
        this.cancellation = new ReschedulingCancellation(reservations, holder, refunds, outbox);
        this.tenants = tenants;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public List<UUID> voidProposals(UUID tenantId, UUID proposedSalidaId, Instant cancelledAt) {
        Instant now = clock.instant();
        return transaction.inTransaction(() -> {
            boolean inactive = tenants.statusOf(tenantId).filter(status -> status == TenantStatus.INACTIVE).isPresent();
            List<UUID> changed = new ArrayList<>();
            for (ReschedulingEpisode episode : episodes.findPendingOn(tenantId, proposedSalidaId)) {
                if (inactive) {
                    var reservation = reservations.findByTenantAndId(tenantId, episode.reservationId()).orElseThrow(() -> new ReservationNotFoundException(episode.reservationId()));
                    ReschedulingEpisode closed = episode.voidAndCancel(now);
                    cancellation.cancel(tenantId, reservation, null, now, true);
                    episodes.save(closed);
                } else {
                    holder.releaseReschedulingHold(tenantId, episode.reservationId());
                    episodes.save(episode.voidPending(cancelledAt));
                }
                changed.add(episode.id());
            }
            return changed;
        });
    }
}
